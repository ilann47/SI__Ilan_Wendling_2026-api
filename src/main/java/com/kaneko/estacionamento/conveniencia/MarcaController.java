package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.conveniencia.dto.MarcaRequest;
import com.kaneko.estacionamento.conveniencia.dto.MarcaResponse;
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
 * Endpoints REST do cadastro de Marca ({@code /api/marcas}).
 */
@RestController
@RequestMapping("/api/marcas")
public class MarcaController {

    private final MarcaService service;

    public MarcaController(MarcaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:read', authentication)")
    public Page<MarcaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(principal.organizacaoId(), nome, ativo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:read', authentication)")
    public ResponseEntity<MarcaResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        MarcaResponse response = MarcaResponse.from(service.buscar(principal.organizacaoId(), id));
        return ResponseEntity.ok().eTag(CatalogoConvenienciaHttpVersion.etag(response.version()))
                .body(response);
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:manage', authentication)")
    public ResponseEntity<MarcaResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody MarcaRequest req) {
        MarcaResponse response = MarcaResponse.from(service.criar(principal.organizacaoId(), req));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(CatalogoConvenienciaHttpVersion.etag(response.version())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:manage', authentication)")
    public ResponseEntity<MarcaResponse> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody MarcaRequest req) {
        MarcaResponse response = MarcaResponse.from(service.atualizar(
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
        Marca marca = service.excluir(principal.organizacaoId(), id,
                CatalogoConvenienciaHttpVersion.extrair(ifMatch));
        return ResponseEntity.noContent()
                .eTag(CatalogoConvenienciaHttpVersion.etag(marca.getVersion())).build();
    }
}
