package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.common.web.CatalogSearch;
import com.kaneko.estacionamento.instalacoes.dto.CriarPatioInstalacaoRequest;
import com.kaneko.estacionamento.instalacoes.dto.PatioInstalacaoResponse;
import com.kaneko.estacionamento.inventario.alocacao.CapacidadeTemporal;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
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
@RequestMapping("/api/v1/parking-facilities")
public class InstalacoesController {
    private final InstalacoesService service;

    public InstalacoesController(InstalacoesService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("""
            @rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication) or
            @rbacGuard.hasPermissionInActiveContext('inventory:read', authentication) or
            @rbacGuard.hasPermissionInActiveContext('inventory:manage', authentication)
            """)
    public Page<CatalogSearch.Option> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String name, @RequestParam(required = false) Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listar(principal.organizacaoId(), name, id, CatalogSearch.page(page, size));
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public ResponseEntity<PatioInstalacaoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody CriarPatioInstalacaoRequest request) {
        PatioInstalacaoResponse response = service.criar(principal.organizacaoId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @GetMapping("/management")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public Page<InstalacoesService.LinhaGestao> listarGestao(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String name, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listarGestao(principal.organizacaoId(), name, CatalogSearch.page(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public ResponseEntity<PatioInstalacaoResponse> obter(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        var patio = service.obter(principal.organizacaoId(), id);
        return ResponseEntity.ok().eTag("\"" + patio.version() + "\"").body(patio);
    }

    @GetMapping("/{id}/availability")
    @PreAuthorize("""
            @rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication) and
            @rbacGuard.hasPermissionInActiveContext('inventory:read', authentication)
            """)
    public ResponseEntity<CapacidadeTemporal.Saldo> disponibilidade(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startsAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endsAt,
            @RequestParam(defaultValue = "false") boolean includeTimeline) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.disponibilidade(principal.organizacaoId(), id, startsAt, endsAt, includeTimeline));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public ResponseEntity<PatioInstalacaoResponse> editar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody CriarPatioInstalacaoRequest request) {
        var patio = service.editar(principal.organizacaoId(), id, InstalacaoHttpVersion.ler(ifMatch), request);
        return ResponseEntity.ok().eTag("\"" + patio.version() + "\"").body(patio);
    }
}
