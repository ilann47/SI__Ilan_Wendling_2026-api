package com.kaneko.estacionamento.estoque;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.estoque.dto.OrigemMovimentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stock-movements")
public class OrigemMovimentoController {
    private final OrigemMovimentoService service;
    public OrigemMovimentoController(OrigemMovimentoService service) { this.service = service; }

    @GetMapping("/{id}/origin")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:read', authentication)")
    @Operation(summary = "Documento de origem do movimento", description = "Consulta somente leitura no tenant ativo. ID, número e caminho do documento são omitidos sem permissão no módulo de origem. Ajustes e origens não identificáveis retornam descrição sem link.")
    public OrigemMovimentoResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return service.buscar(principal.organizacaoId(), principal.membershipId(), id);
    }
}
