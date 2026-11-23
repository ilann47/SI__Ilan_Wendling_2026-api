package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventoId}/cobranca-patio")
public class PatioEventoCobrancaController {
    private final PatioEventoService service;

    PatioEventoCobrancaController(PatioEventoService service) {
        this.service = service;
    }

    public record Configuracao(@NotNull PatioEventoService.Modo modo,
                               @NotNull @Positive @Digits(integer = 8, fraction = 6) BigDecimal valor) {}

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:read', authentication)")
    public PatioEventoService.Cobranca consultar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                 @PathVariable long eventoId) {
        return service.consultar(ator.organizacaoId(), eventoId);
    }

    @PutMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:create', authentication)")
    public PatioEventoService.Cobranca configurar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                   @PathVariable long eventoId, @Valid @RequestBody Configuracao req) {
        return service.configurar(ator.organizacaoId(), eventoId, new PatioEventoService.Cobranca(req.modo(), req.valor()));
    }
}
