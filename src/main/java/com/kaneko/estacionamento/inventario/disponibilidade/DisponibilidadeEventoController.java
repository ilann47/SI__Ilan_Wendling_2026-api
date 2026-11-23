package com.kaneko.estacionamento.inventario.disponibilidade;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.inventario.disponibilidade.dto.DisponibilidadeEventoResponse;
import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventoId}/availability")
public class DisponibilidadeEventoController {
    private final DisponibilidadeEventoService service;

    public DisponibilidadeEventoController(DisponibilidadeEventoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasActiveContext(authentication)")
    public DisponibilidadeEventoResponse consultar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestParam(required = false) CategoriaProduto category) {
        return service.consultar(principal.organizacaoId(), eventoId, category);
    }
}
