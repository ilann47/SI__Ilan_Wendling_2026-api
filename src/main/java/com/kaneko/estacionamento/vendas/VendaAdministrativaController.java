package com.kaneko.estacionamento.vendas;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.vendas.dto.CancelarVendaAdministrativaRequest;
import com.kaneko.estacionamento.vendas.dto.VendaAdministrativaRequest;
import com.kaneko.estacionamento.vendas.dto.VendaAdministrativaResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/administrative-sales")
public class VendaAdministrativaController {
    private final VendaAdministrativaService service;
    public VendaAdministrativaController(VendaAdministrativaService service) {
        this.service = service;
    }
    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('sales:read', authentication)")
    public Page<VendaAdministrativaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) StatusVendaAdministrativa status, Pageable pageable) {
        return service.listar(principal.organizacaoId(), numero, status, pageable)
                .map(VendaAdministrativaResponse::from);
    }
    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('sales:read', authentication)")
    public ResponseEntity<VendaAdministrativaResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        var response = VendaAdministrativaResponse.from(
                service.buscar(principal.organizacaoId(), id));
        return ResponseEntity.ok().eTag(VendaHttpVersion.etag(response.version())).body(response);
    }
    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('sales:manage', authentication)")
    public ResponseEntity<VendaAdministrativaResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody VendaAdministrativaRequest request) {
        var response = VendaAdministrativaResponse.from(service.criar(
                principal.organizacaoId(), principal.userId(), chave, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag(VendaHttpVersion.etag(response.version())).body(response);
    }
    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('sales:manage', authentication)")
    public ResponseEntity<VendaAdministrativaResponse> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody VendaAdministrativaRequest request) {
        var response = VendaAdministrativaResponse.from(service.atualizar(
                principal.organizacaoId(), id, VendaHttpVersion.extrair(ifMatch), request));
        return ResponseEntity.ok().eTag(VendaHttpVersion.etag(response.version())).body(response);
    }
    @PostMapping("/{id}/confirmation")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('sales:manage', authentication)")
    public ResponseEntity<VendaAdministrativaResponse> confirmar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        var response = VendaAdministrativaResponse.from(service.confirmar(
                principal.organizacaoId(), principal.userId(), id,
                VendaHttpVersion.extrair(ifMatch)));
        return ResponseEntity.ok().eTag(VendaHttpVersion.etag(response.version())).body(response);
    }
    @PostMapping("/{id}/cancellation")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('sales:manage', authentication)")
    public ResponseEntity<VendaAdministrativaResponse> cancelar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody CancelarVendaAdministrativaRequest request) {
        var response = VendaAdministrativaResponse.from(service.cancelar(
                principal.organizacaoId(), id, VendaHttpVersion.extrair(ifMatch),
                request.motivo()));
        return ResponseEntity.ok().eTag(VendaHttpVersion.etag(response.version())).body(response);
    }
}
