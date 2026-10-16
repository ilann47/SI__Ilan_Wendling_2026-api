package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.logistica.dto.TransportadoraRequest;
import com.kaneko.estacionamento.logistica.dto.TransportadoraResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST do cadastro de Transportadora ({@code /api/transportadoras}).
 */
@RestController
@RequestMapping("/api/transportadoras")
public class TransportadoraController {

    private final TransportadoraService service;

    public TransportadoraController(TransportadoraService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:read', authentication)")
    public Page<TransportadoraResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String documento,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(principal.organizacaoId(), nome, documento, ativo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:read', authentication)")
    public TransportadoraResponse buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return TransportadoraResponse.from(service.buscar(principal.organizacaoId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:manage', authentication)")
    public TransportadoraResponse criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody TransportadoraRequest req) {
        return TransportadoraResponse.from(service.criar(principal.organizacaoId(), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:manage', authentication)")
    public TransportadoraResponse atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id,
            @Valid @RequestBody TransportadoraRequest req) {
        return TransportadoraResponse.from(
                service.atualizar(principal.organizacaoId(), id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:manage', authentication)")
    public void excluir(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        service.excluir(principal.organizacaoId(), id);
    }
}
