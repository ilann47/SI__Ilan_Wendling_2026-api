package com.kaneko.estacionamento.estoque;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.estoque.dto.LocalEstoqueRequest;
import com.kaneko.estacionamento.estoque.dto.LocalEstoqueResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/stock-locations")
public class LocalEstoqueController {
    private final LocalEstoqueService service;

    public LocalEstoqueController(LocalEstoqueService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:read', authentication)")
    public Page<LocalEstoqueResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal, Pageable pageable) {
        return service.listar(principal.organizacaoId(), pageable).map(LocalEstoqueResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:read', authentication)")
    public ResponseEntity<LocalEstoqueResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        LocalEstoqueResponse response = LocalEstoqueResponse.from(
                service.buscar(principal.organizacaoId(), id));
        return ResponseEntity.ok().eTag(EstoqueHttpVersion.etag(response.version())).body(response);
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:manage', authentication)")
    public ResponseEntity<LocalEstoqueResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody LocalEstoqueRequest request) {
        LocalEstoqueResponse response = LocalEstoqueResponse.from(
                service.criar(principal.organizacaoId(), request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag(EstoqueHttpVersion.etag(response.version())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:manage', authentication)")
    public ResponseEntity<LocalEstoqueResponse> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody LocalEstoqueRequest request) {
        LocalEstoqueResponse response = LocalEstoqueResponse.from(service.atualizar(
                principal.organizacaoId(), id, EstoqueHttpVersion.extrair(ifMatch), request));
        return ResponseEntity.ok().eTag(EstoqueHttpVersion.etag(response.version())).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:manage', authentication)")
    public ResponseEntity<Void> excluir(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        LocalEstoque local = service.excluir(principal.organizacaoId(), id,
                EstoqueHttpVersion.extrair(ifMatch));
        return ResponseEntity.noContent()
                .eTag(EstoqueHttpVersion.etag(local.getVersion())).build();
    }
}
