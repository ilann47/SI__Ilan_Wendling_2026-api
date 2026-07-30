package com.kaneko.estacionamento.pedido;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.pedido.dto.ConfirmarPedidoManualmenteRequest;
import com.kaneko.estacionamento.pedido.dto.CancelamentoPedidoResponse;
import com.kaneko.estacionamento.pedido.dto.CancelarPedidoRequest;
import com.kaneko.estacionamento.pedido.dto.CriarPedidoRequest;
import com.kaneko.estacionamento.pedido.dto.PedidoResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern ETAG = Pattern.compile("^\"([0-9]+)\"$");
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

    @PostMapping("/{orderId}/manual-confirmation")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('orders:manual-confirm', authentication)")
    public ResponseEntity<PedidoResponse> confirmarManualmente(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long orderId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody ConfirmarPedidoManualmenteRequest request) {
        PedidoResponse response = service.confirmarManualmente(
                principal.organizacaoId(), principal.userId(), orderId,
                versao(ifMatch), chaveIdempotencia, request);
        return ResponseEntity.accepted()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{orderId}/cancellation")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('orders:cancel', authentication)")
    public ResponseEntity<CancelamentoPedidoResponse> cancelar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long orderId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody CancelarPedidoRequest request) {
        CancelamentoPedidoResponse response = service.cancelar(
                principal.organizacaoId(), principal.userId(), orderId,
                versao(ifMatch), chaveIdempotencia, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .build().toUri();
        return ResponseEntity.accepted()
                .location(location)
                .eTag("\"" + response.orderVersion() + "\"")
                .body(response);
    }

    private static long versao(String ifMatch) {
        Matcher matcher = ETAG.matcher(ifMatch == null ? "" : ifMatch.trim());
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
