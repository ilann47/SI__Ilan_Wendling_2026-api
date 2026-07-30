package com.kaneko.estacionamento.evento;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.evento.dto.CriarEventoRequest;
import com.kaneko.estacionamento.evento.dto.EventoResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/events")
public class EventoController {
    private final EventoService service;

    public EventoController(EventoService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:create', authentication)")
    public ResponseEntity<EventoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody CriarEventoRequest request) {
        EventoResponse response = service.criar(
                principal.organizacaoId(), principal.userId(), chaveIdempotencia, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
