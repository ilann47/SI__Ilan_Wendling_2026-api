package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
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
 * Endpoints REST dos titulos a receber ({@code /api/contas-receber}). O
 * lancamento e manual; titulos de vendas sao gerados pela confirmacao da nota
 * de saida.
 */
@RestController
@RequestMapping("/api/contas-receber")
public class ContaReceberController {

    private final ContaReceberService service;

    public ContaReceberController(ContaReceberService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:read', authentication)")
    public Page<ContaReceberResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            Pageable pageable) {
        return service.listar(org(principal), pageable).map(ContaReceberResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:read', authentication)")
    public ContaReceberResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return ContaReceberResponse.from(service.buscar(org(principal), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaReceberResponse criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody ContaReceberRequest req) {
        return ContaReceberResponse.from(service.criar(org(principal), req));
    }

    @PostMapping("/{id}/baixa")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaReceberResponse baixar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @Valid @RequestBody BaixaRequest req) {
        return ContaReceberResponse.from(service.baixar(org(principal), id, req));
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaReceberResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return ContaReceberResponse.from(service.cancelar(org(principal), id));
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
