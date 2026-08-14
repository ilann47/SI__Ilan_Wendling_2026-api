package com.kaneko.estacionamento.compras;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.compras.dto.CancelarOrdemCompraRequest;
import com.kaneko.estacionamento.compras.dto.OrdemCompraRequest;
import com.kaneko.estacionamento.compras.dto.OrdemCompraResponse;
import com.kaneko.estacionamento.compras.dto.ReceberOrdemCompraRequest;
import com.kaneko.estacionamento.compras.dto.RecebimentoCompraResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/v1/purchase-orders")
public class OrdemCompraController {

    private final OrdemCompraService service;

    public OrdemCompraController(OrdemCompraService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('purchases:read', authentication)")
    public Page<OrdemCompraResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal, Pageable pageable) {
        return service.listar(principal.organizacaoId(), pageable).map(OrdemCompraResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('purchases:read', authentication)")
    public ResponseEntity<OrdemCompraResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        OrdemCompraResponse response = OrdemCompraResponse.from(
                service.buscar(principal.organizacaoId(), id));
        return ResponseEntity.ok().eTag(CompraHttpVersion.etag(response.version())).body(response);
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('purchases:manage', authentication)")
    public ResponseEntity<OrdemCompraResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody OrdemCompraRequest request) {
        OrdemCompraResponse response = OrdemCompraResponse.from(service.criar(
                principal.organizacaoId(), principal.userId(), chave, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag(CompraHttpVersion.etag(response.version())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('purchases:manage', authentication)")
    public ResponseEntity<OrdemCompraResponse> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody OrdemCompraRequest request) {
        OrdemCompraResponse response = OrdemCompraResponse.from(service.atualizar(
                principal.organizacaoId(), id, CompraHttpVersion.extrair(ifMatch), request));
        return ResponseEntity.ok().eTag(CompraHttpVersion.etag(response.version())).body(response);
    }

    @PostMapping("/{id}/approval")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('purchases:manage', authentication)")
    public ResponseEntity<OrdemCompraResponse> aprovar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        OrdemCompraResponse response = OrdemCompraResponse.from(service.aprovar(
                principal.organizacaoId(), id, CompraHttpVersion.extrair(ifMatch)));
        return ResponseEntity.ok().eTag(CompraHttpVersion.etag(response.version())).body(response);
    }

    @PostMapping("/{id}/cancellation")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('purchases:manage', authentication)")
    public ResponseEntity<OrdemCompraResponse> cancelar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody CancelarOrdemCompraRequest request) {
        OrdemCompraResponse response = OrdemCompraResponse.from(service.cancelar(
                principal.organizacaoId(), id, CompraHttpVersion.extrair(ifMatch),
                request.motivo()));
        return ResponseEntity.ok().eTag(CompraHttpVersion.etag(response.version())).body(response);
    }

    @GetMapping("/{id}/receipts")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('purchases:read', authentication)")
    public List<RecebimentoCompraResponse> listarRecebimentos(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return service.listarRecebimentos(principal.organizacaoId(), id).stream()
                .map(RecebimentoCompraResponse::from).toList();
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('purchases:manage', authentication)")
    public ResponseEntity<RecebimentoCompraResponse> receber(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody ReceberOrdemCompraRequest request) {
        OrdemCompraService.RecebimentoRegistrado resultado = service.receber(
                principal.organizacaoId(), principal.userId(), id,
                CompraHttpVersion.extrair(ifMatch), chave, request);
        RecebimentoCompraResponse response = RecebimentoCompraResponse.from(
                resultado.recebimento());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{recebimentoId}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag(CompraHttpVersion.etag(resultado.ordem().getVersion())).body(response);
    }
}
