package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.conveniencia.dto.CategoriaRequest;
import com.kaneko.estacionamento.conveniencia.dto.CategoriaResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST do cadastro de Categoria ({@code /api/categorias}).
 */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:read', authentication)")
    public Page<CategoriaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(principal.organizacaoId(), nome, ativo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:read', authentication)")
    public ResponseEntity<CategoriaResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        CategoriaResponse response = CategoriaResponse.from(
                service.buscar(principal.organizacaoId(), id));
        return ResponseEntity.ok().eTag(CatalogoConvenienciaHttpVersion.etag(response.version()))
                .body(response);
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:manage', authentication)")
    public ResponseEntity<CategoriaResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody CategoriaRequest req) {
        CategoriaResponse response = CategoriaResponse.from(
                service.criar(principal.organizacaoId(), req));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(CatalogoConvenienciaHttpVersion.etag(response.version())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:manage', authentication)")
    public ResponseEntity<CategoriaResponse> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody CategoriaRequest req) {
        CategoriaResponse response = CategoriaResponse.from(service.atualizar(
                principal.organizacaoId(), id,
                CatalogoConvenienciaHttpVersion.extrair(ifMatch), req));
        return ResponseEntity.ok().eTag(CatalogoConvenienciaHttpVersion.etag(response.version()))
                .body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:manage', authentication)")
    public ResponseEntity<Void> excluir(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        Categoria categoria = service.excluir(principal.organizacaoId(), id,
                CatalogoConvenienciaHttpVersion.extrair(ifMatch));
        return ResponseEntity.noContent()
                .eTag(CatalogoConvenienciaHttpVersion.etag(categoria.getVersion())).build();
    }
}
