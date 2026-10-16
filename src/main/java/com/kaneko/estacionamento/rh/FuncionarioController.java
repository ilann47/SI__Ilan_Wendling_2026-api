package com.kaneko.estacionamento.rh;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.rh.dto.FuncionarioRequest;
import com.kaneko.estacionamento.rh.dto.FuncionarioResponse;
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
 * Endpoints REST do cadastro de Funcionario ({@code /api/funcionarios}).
 */
@RestController
@RequestMapping("/api/funcionarios")
public class FuncionarioController {

    private final FuncionarioService service;

    public FuncionarioController(FuncionarioService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('workforce:read', authentication)")
    public Page<FuncionarioResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Long cargoId,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(principal.organizacaoId(), nome, cargoId, ativo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('workforce:read', authentication)")
    public FuncionarioResponse buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return FuncionarioResponse.from(service.buscar(principal.organizacaoId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('workforce:manage', authentication)")
    public FuncionarioResponse criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody FuncionarioRequest req) {
        return FuncionarioResponse.from(service.criar(principal.organizacaoId(), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('workforce:manage', authentication)")
    public FuncionarioResponse atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id,
            @Valid @RequestBody FuncionarioRequest req) {
        return FuncionarioResponse.from(
                service.atualizar(principal.organizacaoId(), id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('workforce:manage', authentication)")
    public void excluir(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        service.excluir(principal.organizacaoId(), id);
    }
}
