package com.kaneko.estacionamento.credencial;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.credencial.dto.CredencialAcessoResponse;
import com.kaneko.estacionamento.credencial.dto.EmitirCredencialRequest;
import com.kaneko.estacionamento.credencial.dto.QrCodeResponse;
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
@RequestMapping("/api/v1")
public class CredencialAcessoController {
    private static final Pattern ETAG = Pattern.compile("^\"([0-9]+)\"$");
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

    @PostMapping("/credentials/{credentialId}/qr-code")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('credentials:issue', authentication)")
    public ResponseEntity<QrCodeResponse> gerarQr(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long credentialId,
            @RequestHeader("If-Match") String ifMatch) {
        QrCodeResponse response = service.gerarQr(
                principal.organizacaoId(), principal.userId(), credentialId,
                versao(ifMatch));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .build().toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.credentialVersion() + "\"")
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
