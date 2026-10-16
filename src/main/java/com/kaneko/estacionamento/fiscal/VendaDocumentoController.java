package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.fiscal.dto.*;
import com.kaneko.estacionamento.rbac.RbacGuard;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/administrative-sales/{id}")
public class VendaDocumentoController {
    private final VendaDocumentoService service;
    private final RbacGuard guard;
    public VendaDocumentoController(VendaDocumentoService service, RbacGuard guard) {
        this.service = service; this.guard = guard;
    }

    @GetMapping("/documents")
    @Operation(summary = "Documentos, recebíveis e estoque do pedido, filtrados por permissão")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('sales:read', authentication)")
    public DocumentosVendaResponse documentos(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, Authentication authentication) {
        return service.documentos(principal.organizacaoId(), id,
                guard.hasPermissionInActiveContext("fiscal:read", authentication),
                guard.hasPermissionInActiveContext("finance:read", authentication),
                guard.hasPermissionInActiveContext("stock:read", authentication));
    }

    @PostMapping("/outbound-note")
    @Operation(summary = "Prepara nota interna a partir de venda confirmada; não emite NF-e nem repete lançamentos")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('sales:read', authentication) "
            + "and @rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<NotaSaidaResponse> gerar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody GerarNotaVendaRequest request) {
        var response = service.gerar(principal.organizacaoId(), principal.userId(), id, chave, request);
        return ResponseEntity.created(URI.create("/api/notas-saida/" + response.id()))
                .eTag("\"" + response.version() + "\"").body(response);
    }
}
