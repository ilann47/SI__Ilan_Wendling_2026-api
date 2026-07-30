package com.kaneko.estacionamento.credencial;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.credencial.dto.CredencialAcessoResponse;
import com.kaneko.estacionamento.credencial.dto.EmitirCredencialRequest;
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
@RequestMapping("/api/v1")
public class CredencialAcessoController {
    private final CredencialAcessoService service;

    public CredencialAcessoController(CredencialAcessoService service) {
        this.service = service;
    }

    @PostMapping("/orders/{orderId}/credentials")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('credentials:issue', authentication)")
    public ResponseEntity<CredencialAcessoResponse> emitir(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody EmitirCredencialRequest request) {
        CredencialAcessoResponse response = service.emitir(
                principal.organizacaoId(), principal.userId(), orderId,
                chaveIdempotencia, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/credentials/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @GetMapping("/credentials/{credentialId}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('credentials:issue', authentication)")
    public ResponseEntity<CredencialAcessoResponse> consultar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long credentialId) {
        CredencialAcessoResponse response = service.consultar(
                principal.organizacaoId(), principal.userId(), credentialId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
