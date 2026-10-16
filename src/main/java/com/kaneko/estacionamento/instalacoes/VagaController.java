package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.instalacoes.dto.ImportarVagasRequest;
import com.kaneko.estacionamento.instalacoes.dto.ImportarVagasResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sectors/{sectorId}/spaces:batch")
public class VagaController {
    private final VagaService service;

    public VagaController(VagaService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public ResponseEntity<ImportarVagasResponse> importar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long sectorId,
            @Valid @RequestBody ImportarVagasRequest request) {
        ImportarVagasResponse response = service.importar(
                principal.organizacaoId(), sectorId, request);
        return ResponseEntity.status(
                response.possuiResultadoParcial()
                        ? HttpStatus.MULTI_STATUS : HttpStatus.CREATED)
                .body(response);
    }
}
