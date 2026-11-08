package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.dto.NotaSaidaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaDetalheResponse;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.rbac.RbacGuard;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST da nota fiscal de saida ({@code /api/notas-saida}), incluindo a
 * confirmacao (valida e baixa estoque + gera titulos) e o cancelamento.
 */
@RestController
@RequestMapping("/api/notas-saida")
public class NotaSaidaController {

    private final NotaSaidaService service;
    private final NotaSaidaDetalheService detalhe;
    private final RbacGuard guard;

    public NotaSaidaController(NotaSaidaService service, NotaSaidaDetalheService detalhe, RbacGuard guard) {
        this.service = service;
        this.detalhe = detalhe;
        this.guard = guard;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public Page<NotaSaidaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String numero, @RequestParam(required = false) SituacaoNota situacao,
            Pageable pageable) {
        return service.listar(org(principal), numero, situacao, pageable).map(NotaSaidaResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public ResponseEntity<NotaSaidaResponse> buscar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return resposta(service.buscar(org(principal), id), HttpStatus.OK);
    }

    @GetMapping("/{id}/detalhes")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    @Operation(summary = "Consultar a conferência salva e os efeitos da nota",
            description = "Parcelas e contas exigem finance:read; movimentos exigem stock:read.")
    public NotaSaidaDetalheResponse buscarDetalhes(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, Authentication authentication) {
        return detalhe.buscar(org(principal), id, guard.hasPermissionInActiveContext("finance:read", authentication),
                guard.hasPermissionInActiveContext("stock:read", authentication));
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    @Operation(summary = "Salvar rascunho avulso sem movimentar estoque nem gerar contas",
            description = "Idempotency-Key opcional recupera o mesmo registro quando a carga é igual.")
    public ResponseEntity<NotaSaidaResponse> criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @Valid @RequestBody NotaSaidaRequest req) {
        var nota = chave == null ? service.criar(org(principal), req)
                : service.criarIdempotente(org(principal), principal == null ? null : principal.userId(), chave, req);
        return resposta(nota, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    @Operation(summary = "Editar rascunho e congelar novo cronograma; exige If-Match")
    public ResponseEntity<NotaSaidaResponse> atualizar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody NotaSaidaRequest req) {
        return resposta(service.atualizar(org(principal), id, NotaEntradaHttpVersion.extrair(ifMatch), req), HttpStatus.OK);
    }

    @PostMapping("/{id}/confirmacao")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    @Operation(summary = "Confirmar uma única vez a revisão salva; no tenant exige If-Match",
            description = "A nota gera baixa e títulos atomicamente.")
    public ResponseEntity<NotaSaidaResponse> confirmar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Long org = org(principal);
        Long versao = versaoDaOperacao(org, ifMatch);
        return resposta(service.confirmarComResposta(org, principal == null ? null : principal.userId(), id, versao),
                HttpStatus.OK);
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    @Operation(summary = "Cancelar uma nota não confirmada; no tenant exige If-Match")
    public ResponseEntity<NotaSaidaResponse> cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Long org = org(principal);
        return resposta(service.cancelarComResposta(org, id, versaoDaOperacao(org, ifMatch)), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    @Operation(summary = "Excluir uma nota sem efeito no estoque ou financeiro; no tenant exige If-Match")
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Long org = org(principal);
        service.excluir(org, id, versaoDaOperacao(org, ifMatch));
    }

    private static Long versaoDaOperacao(Long org, String ifMatch) {
        return org == null && ifMatch == null ? null : NotaEntradaHttpVersion.extrair(ifMatch);
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }

    private static ResponseEntity<NotaSaidaResponse> resposta(NotaSaida nota, HttpStatus status) {
        return resposta(NotaSaidaResponse.from(nota), status);
    }

    private static ResponseEntity<NotaSaidaResponse> resposta(NotaSaidaResponse nota, HttpStatus status) {
        return ResponseEntity.status(status).eTag("\"" + nota.version() + "\"").body(nota);
    }
}
