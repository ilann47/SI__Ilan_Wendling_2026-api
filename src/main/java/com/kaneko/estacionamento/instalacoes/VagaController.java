package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.common.web.CatalogSearch;
import com.kaneko.estacionamento.instalacoes.dto.AlterarSituacaoVagaRequest;
import com.kaneko.estacionamento.instalacoes.dto.CriarVagaItemRequest;
import com.kaneko.estacionamento.instalacoes.dto.ImportarVagasRequest;
import com.kaneko.estacionamento.instalacoes.dto.ImportarVagasResponse;
import com.kaneko.estacionamento.instalacoes.dto.VagaDetalhe;
import com.kaneko.estacionamento.instalacoes.dto.VagaResumo;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("@rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication)")
public class VagaController {
    private final VagaService service;

    public VagaController(VagaService service) {
        this.service = service;
    }

    @GetMapping("/spaces")
    public Page<VagaResumo> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String code, @RequestParam(required = false) Long sectorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listar(principal.organizacaoId(), sectorId, code,
                CatalogSearch.page(page, size, Sort.by("codigo", "id")));
    }

    @GetMapping("/spaces/{id}")
    public ResponseEntity<VagaDetalhe> obter(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        var vaga = service.obter(principal.organizacaoId(), id);
        return ResponseEntity.ok().eTag("\"" + vaga.version() + "\"").body(vaga);
    }

    @PutMapping("/spaces/{id}")
    public ResponseEntity<VagaDetalhe> editar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id,
            @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody CriarVagaItemRequest request) {
        var vaga = service.editar(principal.organizacaoId(), id, InstalacaoHttpVersion.ler(ifMatch), request);
        return ResponseEntity.ok().eTag("\"" + vaga.version() + "\"").body(vaga);
    }

    @PatchMapping("/spaces/{id}/status")
    public ResponseEntity<VagaDetalhe> alterarSituacao(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody AlterarSituacaoVagaRequest request) {
        var vaga = service.alterarSituacao(principal.organizacaoId(), id, InstalacaoHttpVersion.ler(ifMatch), request.status());
        return ResponseEntity.ok().eTag("\"" + vaga.version() + "\"").body(vaga);
    }

    @PostMapping("/sectors/{sectorId}/spaces")
    public ResponseEntity<VagaDetalhe> criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long sectorId, @Valid @RequestBody CriarVagaItemRequest request) {
        var vaga = service.criar(principal.organizacaoId(), sectorId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/spaces/{id}").buildAndExpand(vaga.vaga().id()).toUri();
        return ResponseEntity.created(location).eTag("\"" + vaga.version() + "\"").body(vaga);
    }

    @PostMapping("/sectors/{sectorId}/spaces:batch")
    public ResponseEntity<ImportarVagasResponse> importar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long sectorId,
            @Valid @RequestBody ImportarVagasRequest request) {
        ImportarVagasResponse response = service.importar(
                principal.organizacaoId(), sectorId, request);
        return ResponseEntity.status(
                response.possuiResultadoParcial()
                        ? HttpStatus.MULTI_STATUS : HttpStatus.CREATED)
                .body(response);
    }
}
