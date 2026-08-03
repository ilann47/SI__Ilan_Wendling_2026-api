package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.pagamento.dto.CondicaoPagamentoRequest;
import com.kaneko.estacionamento.pagamento.dto.CondicaoPagamentoResponse;
import jakarta.validation.Valid;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST da condicao de pagamento ({@code /api/condicoes-pagamento}).
 */
@RestController
@RequestMapping("/api/condicoes-pagamento")
public class CondicaoPagamentoController {

    private final CondicaoPagamentoService service;

    public CondicaoPagamentoController(CondicaoPagamentoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('payments:read', authentication)")
    public Page<CondicaoPagamentoResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(principal.organizacaoId(), nome, ativo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('payments:read', authentication)")
    public CondicaoPagamentoResponse buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return CondicaoPagamentoResponse.from(service.buscar(principal.organizacaoId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('payments:manage', authentication)")
    public CondicaoPagamentoResponse criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody CondicaoPagamentoRequest req) {
        return CondicaoPagamentoResponse.from(service.criar(principal.organizacaoId(), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('payments:manage', authentication)")
    public CondicaoPagamentoResponse atualizar(@PathVariable Long id,
                                               @AuthenticationPrincipal UsuarioAutenticado principal,
                                               @Valid @RequestBody CondicaoPagamentoRequest req) {
        return CondicaoPagamentoResponse.from(service.atualizar(principal.organizacaoId(), id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('payments:manage', authentication)")
    public void excluir(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        service.excluir(principal.organizacaoId(), id);
    }
}
