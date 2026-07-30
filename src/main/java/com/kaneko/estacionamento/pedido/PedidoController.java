package com.kaneko.estacionamento.pedido;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.pedido.dto.CriarPedidoRequest;
import com.kaneko.estacionamento.pedido.dto.PedidoResponse;
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
@RequestMapping("/api/v1/orders")
public class PedidoController {
    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('orders:create', authentication)")
    public ResponseEntity<PedidoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody CriarPedidoRequest request) {
        PedidoResponse response = service.criar(
                principal.organizacaoId(), principal.userId(),
                chaveIdempotencia, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('orders:create', authentication)")
    public ResponseEntity<PedidoResponse> consultar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long orderId) {
        PedidoResponse response = service.consultar(
                principal.organizacaoId(), principal.userId(), orderId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
