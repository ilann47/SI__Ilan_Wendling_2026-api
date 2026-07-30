package com.kaneko.estacionamento.inventario.produto;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.inventario.produto.dto.CriarLotePrecoRequest;
import com.kaneko.estacionamento.inventario.produto.dto.LotePrecoResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/parking-products/{produtoId}/price-tiers")
public class LotePrecoController {
    private final LotePrecoService service;

    public LotePrecoController(LotePrecoService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('pricing:manage', authentication)")
    public ResponseEntity<LotePrecoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long produtoId,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody CriarLotePrecoRequest request) {
        LotePrecoResponse response = service.criar(
                principal.organizacaoId(), principal.userId(), produtoId,
                chaveIdempotencia, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/price-tiers/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
