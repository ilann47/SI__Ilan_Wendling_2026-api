package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.instalacoes.dto.CriarSetorRequest;
import com.kaneko.estacionamento.instalacoes.dto.SetorResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/parking-facilities/{parkingFacilityId}/sectors")
public class SetorController {
    private final SetorService service;

    public SetorController(SetorService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public ResponseEntity<SetorResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long parkingFacilityId,
            @Valid @RequestBody CriarSetorRequest request) {
        SetorResponse response = service.criar(
                principal.organizacaoId(), parkingFacilityId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/sectors/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
