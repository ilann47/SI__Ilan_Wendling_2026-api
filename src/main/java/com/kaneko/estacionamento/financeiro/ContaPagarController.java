package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarResponse;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST dos titulos a pagar ({@code /api/contas-pagar}). O lancamento e
 * manual; titulos de compras sao gerados pela confirmacao da nota de entrada.
 */
@RestController
@RequestMapping("/api/contas-pagar")
public class ContaPagarController {

    private final ContaPagarService service;

    public ContaPagarController(ContaPagarService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:read', authentication)")
    public Page<ContaPagarResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal, Pageable pageable) {
        return service.listar(org(principal), pageable).map(ContaPagarResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:read', authentication)")
    public ContaPagarResponse buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return ContaPagarResponse.from(service.buscar(org(principal), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaPagarResponse criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody ContaPagarRequest req) {
        return ContaPagarResponse.from(service.criar(org(principal), req));
    }

    @PostMapping("/{id}/baixa")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaPagarResponse baixar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @Valid @RequestBody BaixaRequest req) {
        return ContaPagarResponse.from(service.baixar(org(principal), id, req));
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaPagarResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return ContaPagarResponse.from(service.cancelar(org(principal), id));
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
