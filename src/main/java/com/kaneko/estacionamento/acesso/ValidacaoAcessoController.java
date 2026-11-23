package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.acesso.dto.ValidacaoAcessoResponse;
import com.kaneko.estacionamento.acesso.dto.ValidarAcessoRequest;
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
@RequestMapping("/api/v1/access-validations")
public class ValidacaoAcessoController {
    private final ValidacaoAcessoService service;

    public ValidacaoAcessoController(ValidacaoAcessoService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('access:validate', authentication)")
    public ResponseEntity<ValidacaoAcessoResponse> validar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody ValidarAcessoRequest request) {
        return ResponseEntity.ok(service.validar(
                principal.organizacaoId(), principal.userId(),
                chaveIdempotencia, request));
    }
}
