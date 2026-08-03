package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.logistica.dto.VeiculoFrotaRequest;
import com.kaneko.estacionamento.logistica.dto.VeiculoFrotaResponse;
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
 * Endpoints REST do cadastro de Veiculo de Frota ({@code /api/veiculos-frota}).
 */
@RestController
@RequestMapping("/api/veiculos-frota")
public class VeiculoFrotaController {

    private final VeiculoFrotaService service;

    public VeiculoFrotaController(VeiculoFrotaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:read', authentication)")
    public Page<VeiculoFrotaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String placa,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(principal.organizacaoId(), placa, marca, ativo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:read', authentication)")
    public VeiculoFrotaResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return VeiculoFrotaResponse.from(service.buscar(principal.organizacaoId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:manage', authentication)")
    public VeiculoFrotaResponse criar(@AuthenticationPrincipal UsuarioAutenticado principal, @Valid @RequestBody VeiculoFrotaRequest req) {
        return VeiculoFrotaResponse.from(service.criar(principal.organizacaoId(), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:manage', authentication)")
    public VeiculoFrotaResponse atualizar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id, @Valid @RequestBody VeiculoFrotaRequest req) {
        return VeiculoFrotaResponse.from(service.atualizar(principal.organizacaoId(), id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('logistics:manage', authentication)")
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        service.excluir(principal.organizacaoId(), id);
    }
}
