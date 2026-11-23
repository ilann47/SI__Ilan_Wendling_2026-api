package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.acesso.dto.CheckoutResponse;
import com.kaneko.estacionamento.acesso.dto.RealizarCheckoutRequest;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/check-outs")
public class CheckoutController {
    private final CheckoutService service;

    public CheckoutController(CheckoutService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('access:checkout', authentication)")
    public ResponseEntity<CheckoutResponse> realizar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody RealizarCheckoutRequest request) {
        return ResponseEntity.ok(service.realizarCheckout(
                principal.organizacaoId(), principal.userId(),
                chaveIdempotencia, request));
    }
}
