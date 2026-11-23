package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.common.web.CatalogSearch;
import com.kaneko.estacionamento.instalacoes.dto.CriarSetorRequest;
import com.kaneko.estacionamento.instalacoes.dto.SetorResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class SetorController {
    private final SetorService service;

    public SetorController(SetorService service) {
        this.service = service;
    }

    @GetMapping("/sectors")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public Page<SetorService.Opcao> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String name, @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long parkingFacilityId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listar(principal.organizacaoId(), name, id, parkingFacilityId, CatalogSearch.page(page, size));
    }

    @GetMapping("/sectors/management")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public Page<SetorService.LinhaGestao> listarGestao(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String name, @RequestParam(required = false) Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listarGestao(principal.organizacaoId(), name, id, CatalogSearch.page(page, size));
    }

    @GetMapping("/sectors/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public ResponseEntity<SetorService.DetalheGestao> obter(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        var detalhe = service.obter(principal.organizacaoId(), id);
        return ResponseEntity.ok().eTag("\"" + detalhe.setor().version() + "\"").body(detalhe);
    }

    @PutMapping("/sectors/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public ResponseEntity<SetorService.DetalheGestao> editar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody CriarSetorRequest request) {
        var detalhe = service.editar(principal.organizacaoId(), id, InstalacaoHttpVersion.ler(ifMatch), request);
        return ResponseEntity.ok().eTag("\"" + detalhe.setor().version() + "\"").body(detalhe);
    }

    @PostMapping("/parking-facilities/{parkingFacilityId}/sectors")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
    public ResponseEntity<SetorResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long parkingFacilityId,
            @Valid @RequestBody CriarSetorRequest request) {
        SetorResponse response = service.criar(
                principal.organizacaoId(), parkingFacilityId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/sectors/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
