package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarAvulsaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarAvulsaResponse;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST da despesa avulsa a pagar ({@code /api/contas-pagar-avulsas}),
 * incluindo a baixa e o cancelamento.
 */
@RestController
@RequestMapping("/api/contas-pagar-avulsas")
public class ContaPagarAvulsaController {

    private final ContaPagarAvulsaService service;

    public ContaPagarAvulsaController(ContaPagarAvulsaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:read', authentication)")
    public Page<ContaPagarAvulsaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal, Pageable pageable) {
        return service.listar(org(principal), pageable).map(ContaPagarAvulsaResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:read', authentication)")
    public ContaPagarAvulsaResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return ContaPagarAvulsaResponse.from(service.buscar(org(principal), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaPagarAvulsaResponse criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody ContaPagarAvulsaRequest req) {
        return ContaPagarAvulsaResponse.from(service.criar(org(principal), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaPagarAvulsaResponse atualizar(@AuthenticationPrincipal UsuarioAutenticado principal,
                                              @PathVariable Long id,
                                              @Valid @RequestBody ContaPagarAvulsaRequest req) {
        return ContaPagarAvulsaResponse.from(service.atualizar(org(principal), id, req));
    }

    @PostMapping("/{id}/baixa")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaPagarAvulsaResponse baixar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @Valid @RequestBody BaixaRequest req) {
        return ContaPagarAvulsaResponse.from(service.baixar(org(principal), id, req));
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaPagarAvulsaResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return ContaPagarAvulsaResponse.from(service.cancelar(org(principal), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        service.excluir(org(principal), id);
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
