package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.fiscal.dto.DocumentosCompraResponse;
import com.kaneko.estacionamento.rbac.RbacGuard;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchase-orders/{id}/documents")
public class CompraDocumentoController {
    private final CompraDocumentoService service;
    private final RbacGuard guard;
    public CompraDocumentoController(CompraDocumentoService service, RbacGuard guard) {
        this.service = service; this.guard = guard;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('purchases:read', authentication)")
    @Operation(summary = "Consultar recebimentos, notas e efeitos da compra",
            description = "Escopo do tenant ativo. Seções fiscais, financeiras e de estoque são null quando a respectiva permissão de leitura não está presente; listas vazias representam ausência de registros.")
    public DocumentosCompraResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, Authentication authentication) {
        return service.buscar(principal.organizacaoId(), id,
                guard.hasPermissionInActiveContext("fiscal:read", authentication),
                guard.hasPermissionInActiveContext("finance:read", authentication),
                guard.hasPermissionInActiveContext("stock:read", authentication));
    }
}
