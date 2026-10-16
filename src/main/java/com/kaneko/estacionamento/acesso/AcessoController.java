package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.acesso.dto.CheckinResponse;
import com.kaneko.estacionamento.acesso.dto.RealizarCheckinRequest;
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
@RequestMapping("/api/v1/check-ins")
public class AcessoController {
    private final AcessoService service;

    public AcessoController(AcessoService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('access:checkin', authentication)")
    public ResponseEntity<CheckinResponse> realizarCheckin(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody RealizarCheckinRequest request) {
        return ResponseEntity.ok(service.realizarCheckin(
                principal.organizacaoId(), principal.userId(),
                chaveIdempotencia, request));
    }
}
