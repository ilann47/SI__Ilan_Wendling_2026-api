package com.kaneko.estacionamento.ordensservico;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.ordensservico.dto.CancelarOrdemServicoRequest;
import com.kaneko.estacionamento.ordensservico.dto.OrdemServicoRequest;
import com.kaneko.estacionamento.ordensservico.dto.OrdemServicoResponse;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/service-orders")
public class OrdemServicoController {
    private final OrdemServicoService service;
    public OrdemServicoController(OrdemServicoService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('service_orders:read', authentication)")
    public Page<OrdemServicoResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal, Pageable pageable) {
        return service.listar(principal.organizacaoId(), pageable).map(OrdemServicoResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('service_orders:read', authentication)")
    public ResponseEntity<OrdemServicoResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        var response = OrdemServicoResponse.from(service.buscar(principal.organizacaoId(), id));
        return ResponseEntity.ok().eTag(OrdemServicoHttpVersion.etag(response.version())).body(response);
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('service_orders:manage', authentication)")
    public ResponseEntity<OrdemServicoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody OrdemServicoRequest request) {
        var response = OrdemServicoResponse.from(service.criar(principal.organizacaoId(),
                principal.userId(), chave, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag(OrdemServicoHttpVersion.etag(response.version())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('service_orders:manage', authentication)")
    public ResponseEntity<OrdemServicoResponse> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody OrdemServicoRequest request) {
        var response = OrdemServicoResponse.from(service.atualizar(principal.organizacaoId(), id,
                OrdemServicoHttpVersion.extrair(ifMatch), request));
        return ResponseEntity.ok().eTag(OrdemServicoHttpVersion.etag(response.version())).body(response);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('service_orders:manage', authentication)")
    public ResponseEntity<OrdemServicoResponse> iniciar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        var response = OrdemServicoResponse.from(service.iniciar(principal.organizacaoId(), id,
                OrdemServicoHttpVersion.extrair(ifMatch)));
        return ResponseEntity.ok().eTag(OrdemServicoHttpVersion.etag(response.version())).body(response);
    }

    @PostMapping("/{id}/completion")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('service_orders:manage', authentication)")
    public ResponseEntity<OrdemServicoResponse> concluir(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        var response = OrdemServicoResponse.from(service.concluir(principal.organizacaoId(), id,
                OrdemServicoHttpVersion.extrair(ifMatch)));
        return ResponseEntity.ok().eTag(OrdemServicoHttpVersion.etag(response.version())).body(response);
    }

    @PostMapping("/{id}/cancellation")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('service_orders:manage', authentication)")
    public ResponseEntity<OrdemServicoResponse> cancelar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody CancelarOrdemServicoRequest request) {
        var response = OrdemServicoResponse.from(service.cancelar(principal.organizacaoId(), id,
                OrdemServicoHttpVersion.extrair(ifMatch), request.motivo()));
        return ResponseEntity.ok().eTag(OrdemServicoHttpVersion.etag(response.version())).body(response);
    }
}
