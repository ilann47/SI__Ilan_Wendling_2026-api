package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.dto.NotaServicoRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoResponse;
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
 * Endpoints REST da nota de servico ({@code /api/notas-servico}), incluindo a
 * emissao e o cancelamento.
 */
@RestController
@RequestMapping("/api/notas-servico")
public class NotaServicoController {

    private final NotaServicoService service;

    public NotaServicoController(NotaServicoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public Page<NotaServicoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            Pageable pageable) {
        return service.listar(org(principal), pageable).map(NotaServicoResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public NotaServicoResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return NotaServicoResponse.from(service.buscar(org(principal), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaServicoResponse criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody NotaServicoRequest req) {
        return NotaServicoResponse.from(service.criar(org(principal), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaServicoResponse atualizar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @Valid @RequestBody NotaServicoRequest req) {
        return NotaServicoResponse.from(service.atualizar(org(principal), id, req));
    }

    @PostMapping("/{id}/emissao")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaServicoResponse emitir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return NotaServicoResponse.from(service.emitir(org(principal), id));
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaServicoResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return NotaServicoResponse.from(service.cancelar(org(principal), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        service.excluir(org(principal), id);
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
