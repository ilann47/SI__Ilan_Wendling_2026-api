package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.logistica.dto.TransportadoraVeiculoFrotaRequest;
import com.kaneko.estacionamento.logistica.dto.TransportadoraVeiculoFrotaResponse;
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
 * Endpoints REST do vinculo transportadora-veiculo/frota
 * ({@code /api/transportadora-veiculos}).
 */
@RestController
@RequestMapping("/api/transportadora-veiculos")
public class TransportadoraVeiculoFrotaController {

    private final TransportadoraVeiculoFrotaService service;

    public TransportadoraVeiculoFrotaController(TransportadoraVeiculoFrotaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:read', authentication)")
    public Page<TransportadoraVeiculoFrotaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal, Pageable pageable) {
        return service.listar(principal.organizacaoId(), pageable).map(TransportadoraVeiculoFrotaResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:read', authentication)")
    public TransportadoraVeiculoFrotaResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return TransportadoraVeiculoFrotaResponse.from(service.buscar(principal.organizacaoId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:manage', authentication)")
    public TransportadoraVeiculoFrotaResponse criar(@AuthenticationPrincipal UsuarioAutenticado principal, @Valid @RequestBody TransportadoraVeiculoFrotaRequest req) {
        return TransportadoraVeiculoFrotaResponse.from(service.criar(principal.organizacaoId(), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:manage', authentication)")
    public TransportadoraVeiculoFrotaResponse atualizar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
                                                        @Valid @RequestBody TransportadoraVeiculoFrotaRequest req) {
        return TransportadoraVeiculoFrotaResponse.from(service.atualizar(principal.organizacaoId(), id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:manage', authentication)")
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        service.excluir(principal.organizacaoId(), id);
    }
}
