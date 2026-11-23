package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.dto.NotaServicoRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoDetalheResponse;
import com.kaneko.estacionamento.fiscal.dto.CancelarNotaServicoRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST da nota de servico ({@code /api/notas-servico}), incluindo a
 * emissao e o cancelamento.
 */
@RestController
@RequestMapping("/api/notas-servico")
public class NotaServicoController {

    private final NotaServicoService service;
    private final RbacGuard guard;

    public NotaServicoController(NotaServicoService service, RbacGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public Page<NotaServicoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String numero, @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) SituacaoNotaServico situacao,
            Pageable pageable) {
        return service.consultar(org(principal), numero, clienteId, situacao, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public ResponseEntity<NotaServicoResponse> buscar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return responder(NotaServicoResponse.from(service.buscar(org(principal), id)), HttpStatus.OK);
    }

    @GetMapping("/{id}/detalhes")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    @Operation(summary = "Consultar itens, parcelas salvas e contas da Nota de Serviço",
            description = "Contas geradas são incluídas somente com finance:read.")
    public NotaServicoDetalheResponse detalhes(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, Authentication authentication) {
        return service.detalhes(org(principal), id,
                guard.hasPermissionInActiveContext("finance:read", authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<NotaServicoResponse> criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody NotaServicoRequest req) {
        return responder(NotaServicoResponse.from(service.criar(org(principal), req)), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<NotaServicoResponse> atualizar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = "If-Match", required = false) String versao,
            @Valid @RequestBody NotaServicoRequest req) {
        return responder(NotaServicoResponse.from(service.atualizar(org(principal), id,
                versao(org(principal), versao), req)), HttpStatus.OK);
    }

    @PostMapping("/{id}/emissao")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    @Operation(summary = "Confirmar a revisão da nota e gerar as parcelas salvas, sem transmissão fiscal",
            description = "Na organização ativa exige If-Match da versão conferida. Rascunho, versão e títulos são validados em transação.")
    public ResponseEntity<NotaServicoResponse> emitir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = "If-Match", required = false) String versao) {
        return responder(service.emitir(org(principal), id, versao(org(principal), versao)), HttpStatus.OK);
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    @Operation(summary = "Cancelar internamente com motivo, preservando a nota e suas contas")
    public ResponseEntity<NotaServicoResponse> cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = "If-Match", required = false) String versao,
            @Valid @RequestBody CancelarNotaServicoRequest req) {
        return responder(service.cancelar(org(principal), id, versao(org(principal), versao), req.motivo()), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = "If-Match", required = false) String versao) {
        service.excluir(org(principal), id, versao(org(principal), versao));
    }

    private static Long versao(Long org, String ifMatch) {
        return org == null && ifMatch == null ? null : NotaEntradaHttpVersion.extrair(ifMatch);
    }

    private static ResponseEntity<NotaServicoResponse> responder(NotaServicoResponse nota, HttpStatus status) {
        return ResponseEntity.status(status).eTag("\"" + nota.version() + "\"").body(nota);
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
