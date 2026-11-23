package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.evento.dto.EventoResponse;
import com.kaneko.estacionamento.evento.dto.PublicarEventoRequest;
import com.kaneko.estacionamento.rbac.RbacGuard;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class PatioEventoOperacaoController {
    private final PatioEventoOperacaoService service;
    private final RbacGuard guard;
    public PatioEventoOperacaoController(PatioEventoOperacaoService service, RbacGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/{id}/patio-operation-summary")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:read', authentication)")
    public PatioEventoOperacaoService.Resumo resumo(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                    @PathVariable long id, Authentication authentication) {
        return service.resumo(ator.organizacaoId(), id,
                guard.hasPermissionInActiveContext("finance:read", authentication),
                guard.hasPermissionInActiveContext("fiscal:read", authentication));
    }

    @PostMapping("/{id}/patio-operation-start")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('access:operate', authentication)"
            + " and @rbacGuard.hasPermissionInActiveContext('events:publish', authentication)")
    public ResponseEntity<EventoResponse> iniciar(@AuthenticationPrincipal UsuarioAutenticado ator,
            @PathVariable long id, @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody PublicarEventoRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirmed()))
            throw new IllegalArgumentException("Confirme o início da operação avulsa.");
        if (ifMatch == null || !ifMatch.matches("\"(0|[1-9][0-9]*)\""))
            throw new IllegalArgumentException("Informe a revisão atual do evento.");
        final long version;
        try { version = Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1)); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Revisão do evento inválida."); }
        var result = service.iniciar(ator.organizacaoId(), id, version);
        return ResponseEntity.ok().eTag("\"" + result.version() + "\"").body(result);
    }
}
