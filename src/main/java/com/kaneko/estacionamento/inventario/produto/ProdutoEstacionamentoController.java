package com.kaneko.estacionamento.inventario.produto;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.inventario.produto.dto.CriarProdutoEstacionamentoRequest;
import com.kaneko.estacionamento.inventario.produto.dto.ProdutoEstacionamentoResponse;
import com.kaneko.estacionamento.inventario.produto.dto.PublicarProdutoRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class ProdutoEstacionamentoController {
    private static final Pattern ETAG_VERSAO = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");
    private final ProdutoEstacionamentoService service;

    public ProdutoEstacionamentoController(ProdutoEstacionamentoService service) {
        this.service = service;
    }

    @GetMapping("/events/{eventoId}/parking-products")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('pricing:read', authentication)")
    public List<ProdutoEstacionamentoResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId) {
        return service.listar(principal.organizacaoId(), eventoId);
    }

    @GetMapping("/parking-products/{produtoId}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('pricing:read', authentication)")
    public ResponseEntity<ProdutoEstacionamentoResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long produtoId) {
        ProdutoEstacionamentoResponse response = service.buscar(
                principal.organizacaoId(), produtoId);
        return ResponseEntity.ok().eTag("\"" + response.version() + "\"").body(response);
    }

    @PostMapping("/events/{eventoId}/parking-products")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('pricing:manage', authentication)")
    public ResponseEntity<ProdutoEstacionamentoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody CriarProdutoEstacionamentoRequest request) {
        ProdutoEstacionamentoResponse response = service.criar(
                principal.organizacaoId(), principal.userId(), eventoId,
                chaveIdempotencia, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/parking-products/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/parking-products/{produtoId}/publication")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('pricing:manage', authentication)")
    public ResponseEntity<ProdutoEstacionamentoResponse> publicar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long produtoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody PublicarProdutoRequest request) {
        ProdutoEstacionamentoResponse response = service.publicar(
                principal.organizacaoId(), produtoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    private static long extrairVersao(String ifMatch) {
        Matcher matcher = ETAG_VERSAO.matcher(ifMatch);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "If-Match deve conter somente a versao esperada entre aspas.");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "A versao informada em If-Match e invalida.", ex);
        }
    }
}
