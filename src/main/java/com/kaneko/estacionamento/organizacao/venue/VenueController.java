package com.kaneko.estacionamento.organizacao.venue;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.common.web.CatalogSearch;
import com.kaneko.estacionamento.instalacoes.InstalacaoHttpVersion;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import com.kaneko.estacionamento.organizacao.venue.dto.VenueResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {
    private final VenueService service;

    public VenueController(VenueService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("""
            @rbacGuard.hasPermissionInActiveContext('organizations:admin', authentication) or
            @rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication) or
            @rbacGuard.hasPermissionInActiveContext('events:create', authentication)
            """)
    public Page<CatalogSearch.Option> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String name, @RequestParam(required = false) Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listar(principal.organizacaoId(), name, id, CatalogSearch.page(page, size));
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('organizations:admin', authentication)")
    public ResponseEntity<VenueResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody CriarVenueRequest request) {
        VenueResponse response = service.criar(principal.organizacaoId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @GetMapping("/management")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('organizations:admin', authentication)")
    public Page<CatalogSearch.ManagementRow> listarGestao(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String name, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listarGestao(principal.organizacaoId(), name, CatalogSearch.page(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('organizations:admin', authentication)")
    public ResponseEntity<VenueResponse> obter(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        var local = service.obter(principal.organizacaoId(), id);
        return ResponseEntity.ok().eTag("\"" + local.version() + "\"").body(local);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('organizations:admin', authentication)")
    public ResponseEntity<VenueResponse> editar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody CriarVenueRequest request) {
        var local = service.editar(principal.organizacaoId(), id, InstalacaoHttpVersion.ler(ifMatch), request);
        return ResponseEntity.ok().eTag("\"" + local.version() + "\"").body(local);
    }
}
