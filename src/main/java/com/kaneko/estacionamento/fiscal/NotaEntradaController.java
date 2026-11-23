package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.dto.NotaEntradaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaDetalheResponse;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.rbac.RbacGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.kaneko.estacionamento.fiscal.dto.PreviaNotaEntradaResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST da nota fiscal de entrada ({@code /api/notas-entrada}),
 * incluindo a confirmacao (efetiva estoque + gera titulos) e o cancelamento.
 */
@RestController
@RequestMapping("/api/notas-entrada")
public class NotaEntradaController {

    private final NotaEntradaService service;
    private final NotaEntradaDetalheService detalheService;
    private final RbacGuard guard;

    public NotaEntradaController(
            NotaEntradaService service,
            NotaEntradaDetalheService detalheService, RbacGuard guard) {
        this.service = service;
        this.detalheService = detalheService;
        this.guard = guard;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public Page<NotaEntradaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required=false) String numero, @RequestParam(required=false) SituacaoNota situacao,
            @RequestParam(required=false) Long fornecedorId, Pageable pageable) {
        return service.listar(org(principal), numero, situacao, fornecedorId, pageable).map(NotaEntradaResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public ResponseEntity<NotaEntradaResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return resposta(service.buscar(org(principal), id));
    }

    @GetMapping("/{id}/detalhes")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    @Operation(summary = "Consultar detalhes integrados da nota de entrada",
            description = "Retorna o documento, os titulos a pagar e os movimentos fisicos "
                    + "do estoque no tenant ativo. Seções sem finance:read ou stock:read retornam null.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes encontrados"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido"),
            @ApiResponse(responseCode = "403", description = "Sem permissao fiscal de leitura"),
            @ApiResponse(responseCode = "404", description = "Nota inexistente ou de outro tenant")
    })
    public NotaEntradaDetalheResponse buscarDetalhes(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            Authentication authentication) {
        return detalheService.buscar(org(principal), id,
                guard.hasPermissionInActiveContext("finance:read", authentication),
                guard.hasPermissionInActiveContext("stock:read", authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    @Operation(summary = "Registrar nota de entrada pendente",
            description = "Não movimenta estoque nem cria títulos. Idempotency-Key opcional permite recuperar a mesma nota após timeout; a mesma chave com dados diferentes retorna 409.")
    public ResponseEntity<NotaEntradaResponse> criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @Valid @RequestBody NotaEntradaRequest req, Authentication authentication) {
        var nota = chave == null ? service.criar(org(principal), req)
                : service.criarIdempotente(org(principal), principal == null ? null : principal.userId(), chave, req);
        return ResponseEntity.status(HttpStatus.CREATED).eTag("\""+nota.getVersion()+"\"").body(NotaEntradaResponse.from(nota));
    }

    @PostMapping("/{id}/confirmacao")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<NotaEntradaResponse> confirmar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value="If-Match", required=false) String versao) {
        // Sem contexto so existe o modo legado com seguranca explicitamente desabilitada.
        Long esperada = org(principal) == null && versao == null ? null : NotaEntradaHttpVersion.extrair(versao);
        return resposta(service.confirmar(org(principal), principal == null ? null : principal.userId(), id, esperada));
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaEntradaResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value="If-Match", required=false) String versao) {
        return NotaEntradaResponse.from(service.cancelar(org(principal), id,
                versao == null ? null : NotaEntradaHttpVersion.extrair(versao)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value="If-Match", required=false) String versao) {
        service.excluir(org(principal), id, versao == null ? null : NotaEntradaHttpVersion.extrair(versao));
    }

    @PostMapping("/previa")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public PreviaNotaEntradaResponse previa(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody NotaEntradaRequest req, Authentication authentication) {
        return service.previa(org(principal), req);
    }

    @PostMapping("/{id}/previa")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public PreviaNotaEntradaResponse previaEdicao(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader("If-Match") String versao,
            @Valid @RequestBody NotaEntradaRequest req, Authentication authentication) {
        return service.previa(org(principal), id, NotaEntradaHttpVersion.extrair(versao), req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<NotaEntradaResponse> atualizar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader("If-Match") String versao,
            @Valid @RequestBody NotaEntradaRequest req, Authentication authentication) {
        return resposta(service.atualizar(org(principal), id, NotaEntradaHttpVersion.extrair(versao), req));
    }

    private static ResponseEntity<NotaEntradaResponse> resposta(NotaEntrada nota) {
        return ResponseEntity.ok().eTag("\""+nota.getVersion()+"\"").body(NotaEntradaResponse.from(nota));
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
