package com.kaneko.estacionamento.inventario.alocacao;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.inventario.alocacao.dto.AlocacaoPatioEventoResponse;
import com.kaneko.estacionamento.inventario.alocacao.dto.AlterarCapacidadesAlocacaoRequest;
import com.kaneko.estacionamento.inventario.alocacao.dto.CriarAlocacaoPatioEventoRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class AlocacaoPatioEventoController {
    private static final Pattern ETAG_VERSAO = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");

    private final AlocacaoPatioEventoService service;

    public AlocacaoPatioEventoController(AlocacaoPatioEventoService service) {
        this.service = service;
    }

    @PostMapping("/events/{eventoId}/parking-allocations")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('inventory:manage', authentication)")
    public ResponseEntity<AlocacaoPatioEventoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody CriarAlocacaoPatioEventoRequest request) {
        AlocacaoPatioEventoResponse response = service.criar(
                principal.organizacaoId(), principal.userId(), eventoId,
                chaveIdempotencia, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/parking-allocations/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag(etag(response.version()))
                .body(response);
    }

    @PatchMapping("/parking-allocations/{alocacaoId}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('inventory:manage', authentication)")
    public ResponseEntity<AlocacaoPatioEventoResponse> alterarCapacidades(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long alocacaoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody AlterarCapacidadesAlocacaoRequest request) {
        AlocacaoPatioEventoResponse response = service.alterarCapacidades(
                principal.organizacaoId(), alocacaoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag(etag(response.version()))
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
            throw new IllegalArgumentException("A versao informada em If-Match e invalida.", ex);
        }
    }

    private static String etag(Long version) {
        return "\"" + version + "\"";
    }
}
