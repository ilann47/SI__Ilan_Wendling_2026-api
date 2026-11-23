package com.kaneko.estacionamento.consulta;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.consulta.dto.PendenciasOperacionaisResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operational-pendencies")
public class PendenciasOperacionaisController {
    private final PendenciasOperacionaisService service;
    public PendenciasOperacionaisController(PendenciasOperacionaisService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("principal.organizacaoId != null && principal.membershipId != null")
    @Operation(summary = "Pendências reais por processo", description = "Somente grupos permitidos no tenant ativo. Totais completos e até limit itens por grupo (1 a 10), ordenados por data e ID. Vencidas incluem todo o histórico, no fuso da organização.")
    public PendenciasOperacionaisResponse listar(@AuthenticationPrincipal UsuarioAutenticado principal,
                                                 @RequestParam(defaultValue = "5") int limit) {
        return service.listar(principal.organizacaoId(), principal.membershipId(), limit);
    }
}
