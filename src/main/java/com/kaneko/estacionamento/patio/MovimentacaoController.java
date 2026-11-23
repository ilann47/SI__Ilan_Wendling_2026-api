package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.patio.dto.EntradaRequest;
import com.kaneko.estacionamento.patio.dto.MovimentacaoResponse;
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
 * Endpoints REST da operacao de patio ({@code /api/movimentacoes}).
 */
@RestController
@RequestMapping("/api/movimentacoes")
public class MovimentacaoController {

    private final MovimentacaoService service;

    public MovimentacaoController(MovimentacaoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:read', authentication)"
            + " or (principal.organizacaoId() == null and hasAnyRole('ADMIN','OPERADOR'))")
    public Page<MovimentacaoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            Pageable pageable) {
        return service.listar(org(principal), pageable).map(MovimentacaoResponse::from);
    }

    /** Pátio atual: veiculos ainda no estacionamento (movimentacoes abertas). */
    @GetMapping("/patio")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:read', authentication)"
            + " or (principal.organizacaoId() == null and hasAnyRole('ADMIN','OPERADOR'))")
    public Page<MovimentacaoResponse> patio(@AuthenticationPrincipal UsuarioAutenticado principal,
            Pageable pageable) {
        return service.listarPatio(org(principal), pageable).map(MovimentacaoResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:read', authentication)"
            + " or (principal.organizacaoId() == null and hasAnyRole('ADMIN','OPERADOR'))")
    public MovimentacaoResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return MovimentacaoResponse.from(service.buscar(org(principal), id));
    }

    @PostMapping("/entrada")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)"
            + " or (principal.organizacaoId() == null and hasAnyRole('ADMIN','OPERADOR'))")
    public MovimentacaoResponse entrada(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody EntradaRequest req) {
        return MovimentacaoResponse.from(service.registrarEntrada(org(principal), req));
    }

    @PostMapping("/{id}/saida")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)"
            + " or (principal.organizacaoId() == null and hasAnyRole('ADMIN','OPERADOR'))")
    public MovimentacaoResponse saida(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return MovimentacaoResponse.from(service.registrarSaida(org(principal), id));
    }

    /** Cancela uma movimentacao ABERTA (entrada por engano) — RF 4.18.3. */
    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)"
            + " or (principal.organizacaoId() == null and hasAnyRole('ADMIN','OPERADOR'))")
    public MovimentacaoResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return MovimentacaoResponse.from(service.cancelar(org(principal), id));
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
