package com.kaneko.estacionamento.evento;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.evento.dto.AbrirVendasEventoRequest;
import com.kaneko.estacionamento.evento.dto.AtualizarEventoRequest;
import com.kaneko.estacionamento.evento.dto.CriarEventoRequest;
import com.kaneko.estacionamento.evento.dto.EventoResponse;
import com.kaneko.estacionamento.evento.dto.PublicarEventoRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/events")
public class EventoController {
    private static final Pattern ETAG_VERSAO = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");
    private final EventoService service;

    public EventoController(EventoService service) {
        this.service = service;
    }

    @PatchMapping("/{eventoId}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:create', authentication)")
    public ResponseEntity<EventoResponse> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody AtualizarEventoRequest request) {
        EventoResponse response = service.atualizar(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
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

    @PostMapping("/{eventoId}/publication")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:publish', authentication)")
    public ResponseEntity<EventoResponse> publicar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody PublicarEventoRequest request) {
        EventoResponse response = service.publicar(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{eventoId}/sales-opening")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:publish', authentication)")
    public ResponseEntity<EventoResponse> abrirVendas(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody AbrirVendasEventoRequest request) {
        EventoResponse response = service.abrirVendas(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{eventoId}/sales-closing")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:publish', authentication)")
    public ResponseEntity<EventoResponse> encerrarVendas(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody PublicarEventoRequest request) {
        EventoResponse response = service.encerrarVendas(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{eventoId}/operation-start")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('access:operate', authentication)")
    public ResponseEntity<EventoResponse> iniciarOperacao(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody PublicarEventoRequest request) {
        EventoResponse response = service.iniciarOperacao(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    private static long extrairVersao(String ifMatch) {
        Matcher matcher = ETAG_VERSAO.matcher(ifMatch);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "If-Match deve conter somente a versao esperada entre aspas.");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "A versao informada em If-Match e invalida.", ex);
        }
    }
}
