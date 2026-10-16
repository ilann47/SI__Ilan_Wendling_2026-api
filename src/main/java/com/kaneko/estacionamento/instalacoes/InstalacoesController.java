package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.instalacoes.dto.CriarPatioInstalacaoRequest;
import com.kaneko.estacionamento.instalacoes.dto.PatioInstalacaoResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/parking-facilities")
public class InstalacoesController {
    private final InstalacoesService service;

    public InstalacoesController(InstalacoesService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public ResponseEntity<PatioInstalacaoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody CriarPatioInstalacaoRequest request) {
        PatioInstalacaoResponse response = service.criar(principal.organizacaoId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
