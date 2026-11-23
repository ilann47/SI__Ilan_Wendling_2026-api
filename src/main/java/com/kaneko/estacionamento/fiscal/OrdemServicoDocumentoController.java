package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.fiscal.dto.DocumentosOrdemServicoResponse;
import com.kaneko.estacionamento.fiscal.dto.GerarNotaOrdemServicoRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoResponse;
import com.kaneko.estacionamento.rbac.RbacGuard;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-orders/{id}")
public class OrdemServicoDocumentoController {
    private final OrdemServicoDocumentoService service;
    private final RbacGuard guard;
    public OrdemServicoDocumentoController(OrdemServicoDocumentoService service, RbacGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/documents")
    @Operation(summary = "Consulta nota e recebíveis da OS conforme as permissões de cada módulo")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('service_orders:read', authentication)")
    public DocumentosOrdemServicoResponse documentos(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, Authentication authentication) {
        return service.documentos(principal.organizacaoId(), id,
                guard.hasPermissionInActiveContext("fiscal:read", authentication),
                guard.hasPermissionInActiveContext("finance:read", authentication));
    }

    @PostMapping("/service-note")
    @Operation(summary = "Prepara nota interna pendente da OS concluída, sem gerar cobrança nem transmitir NFS-e")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('service_orders:read', authentication) "
            + "and @rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<NotaServicoResponse> gerar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody GerarNotaOrdemServicoRequest request) {
        var response = service.gerar(principal.organizacaoId(), principal.userId(), id, chave, request);
        return ResponseEntity.created(URI.create("/api/notas-servico/" + response.id()))
                .eTag("\"" + response.version() + "\"").body(response);
    }
}
