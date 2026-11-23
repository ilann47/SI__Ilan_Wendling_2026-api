package com.kaneko.estacionamento.inventario.reserva;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.inventario.reserva.dto.CriarReservaInventarioRequest;
import com.kaneko.estacionamento.inventario.reserva.dto.LiberarReservaInventarioRequest;
import com.kaneko.estacionamento.inventario.reserva.dto.ReservaInventarioResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/inventory-holds")
public class ReservaInventarioController {
    private final ReservaInventarioService service;

    public ReservaInventarioController(ReservaInventarioService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('inventory:hold', authentication)")
    public ResponseEntity<ReservaInventarioResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody CriarReservaInventarioRequest request) {
        ReservaInventarioResponse response = service.criar(
                principal.organizacaoId(), principal.userId(), chaveIdempotencia, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @GetMapping("/{holdId}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('inventory:hold', authentication)")
    public ResponseEntity<ReservaInventarioResponse> consultar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long holdId) {
        ReservaInventarioResponse response = service.consultar(
                principal.organizacaoId(), principal.userId(), holdId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{holdId}/release")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('inventory:hold', authentication)")
    public ResponseEntity<ReservaInventarioResponse> liberar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long holdId,
            @Valid @RequestBody LiberarReservaInventarioRequest request) {
        ReservaInventarioResponse response = service.liberar(
                principal.organizacaoId(), principal.userId(), holdId, request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
