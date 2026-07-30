package com.kaneko.estacionamento.organizacao;

import com.kaneko.estacionamento.organizacao.dto.AtualizarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.dto.OrganizacaoResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizacaoController {

    private static final Pattern ETAG_VERSAO = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");

    private final OrganizacaoService service;

    public OrganizacaoController(OrganizacaoService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.platformAdmin(authentication)")
    public ResponseEntity<OrganizacaoResponse> criar(@Valid @RequestBody CriarOrganizacaoRequest request) {
        OrganizacaoResponse response = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(etag(response.version()))
                .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.canReadOrganization(#id, authentication)")
    public ResponseEntity<OrganizacaoResponse> buscar(@PathVariable Long id) {
        OrganizacaoResponse response = service.buscar(id);
        return ResponseEntity.ok()
                .eTag(etag(response.version()))
                .body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermission(#id, 'organizations:admin', authentication)")
    public ResponseEntity<OrganizacaoResponse> atualizar(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody AtualizarOrganizacaoRequest request) {
        OrganizacaoResponse response = service.atualizar(id, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag(etag(response.version()))
                .body(response);
    }

    private static long extrairVersao(String ifMatch) {
        Matcher matcher = ETAG_VERSAO.matcher(ifMatch);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("If-Match deve conter somente a versao esperada entre aspas.");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("A versao informada em If-Match e invalida.", ex);
        }
    }

    private static String etag(Long version) {
        return "\"" + version + "\"";
    }
}
