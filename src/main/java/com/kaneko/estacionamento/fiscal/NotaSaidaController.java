package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.dto.NotaSaidaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST da nota fiscal de saida ({@code /api/notas-saida}), incluindo a
 * confirmacao (valida e baixa estoque + gera titulos) e o cancelamento.
 */
@RestController
@RequestMapping("/api/notas-saida")
public class NotaSaidaController {

    private final NotaSaidaService service;

    public NotaSaidaController(NotaSaidaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public Page<NotaSaidaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            Pageable pageable) {
        return service.listar(org(principal), pageable).map(NotaSaidaResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public NotaSaidaResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return NotaSaidaResponse.from(service.buscar(org(principal), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaSaidaResponse criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody NotaSaidaRequest req) {
        return NotaSaidaResponse.from(service.criar(org(principal), req));
    }

    @PostMapping("/{id}/confirmacao")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaSaidaResponse confirmar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return NotaSaidaResponse.from(service.confirmar(org(principal),
                principal == null ? null : principal.userId(), id));
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaSaidaResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return NotaSaidaResponse.from(service.cancelar(org(principal), id));
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
