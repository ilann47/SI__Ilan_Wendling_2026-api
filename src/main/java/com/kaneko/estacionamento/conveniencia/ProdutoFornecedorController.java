package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.conveniencia.dto.ProdutoFornecedorRequest;
import com.kaneko.estacionamento.conveniencia.dto.ProdutoFornecedorResponse;
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

/** Endpoints tenant-aware do vínculo Produto–Fornecedor. */
@RestController
@RequestMapping("/api/produto-fornecedores")
public class ProdutoFornecedorController {

    private final ProdutoFornecedorService service;

    public ProdutoFornecedorController(ProdutoFornecedorService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:read', authentication)")
    public Page<ProdutoFornecedorResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) Long produtoId, Pageable pageable) {
        return service.listar(principal.organizacaoId(), produtoId, pageable)
                .map(ProdutoFornecedorResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:read', authentication)")
    public ResponseEntity<ProdutoFornecedorResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        ProdutoFornecedorResponse response = ProdutoFornecedorResponse.from(
                service.buscar(principal.organizacaoId(), id));
        return ResponseEntity.ok()
                .eTag(CatalogoConvenienciaHttpVersion.etag(response.version())).body(response);
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:manage', authentication)")
    public ResponseEntity<ProdutoFornecedorResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody ProdutoFornecedorRequest request) {
        ProdutoFornecedorResponse response = ProdutoFornecedorResponse.from(
                service.criar(principal.organizacaoId(), request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(CatalogoConvenienciaHttpVersion.etag(response.version())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:manage', authentication)")
    public ResponseEntity<ProdutoFornecedorResponse> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ProdutoFornecedorRequest request) {
        ProdutoFornecedorResponse response = ProdutoFornecedorResponse.from(
                service.atualizar(principal.organizacaoId(), id,
                        CatalogoConvenienciaHttpVersion.extrair(ifMatch), request));
        return ResponseEntity.ok()
                .eTag(CatalogoConvenienciaHttpVersion.etag(response.version())).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('catalog:manage', authentication)")
    public ResponseEntity<Void> excluir(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        ProdutoFornecedor vinculo = service.excluir(principal.organizacaoId(), id,
                CatalogoConvenienciaHttpVersion.extrair(ifMatch));
        return ResponseEntity.noContent()
                .eTag(CatalogoConvenienciaHttpVersion.etag(vinculo.getVersion())).build();
    }
}
