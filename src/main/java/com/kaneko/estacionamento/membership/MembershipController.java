package com.kaneko.estacionamento.membership;

import com.kaneko.estacionamento.membership.dto.AtualizarMembershipRequest;
import com.kaneko.estacionamento.membership.dto.CriarMembershipRequest;
import com.kaneko.estacionamento.membership.dto.MembershipResponse;
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

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/memberships")
public class MembershipController {

    private static final Pattern ETAG_VERSAO = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");

    private final MembershipService service;

    public MembershipController(MembershipService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MembershipResponse> criar(
            @PathVariable Long organizationId,
            @Valid @RequestBody CriarMembershipRequest request) {
        MembershipResponse response = service.criar(organizationId, request.getUsuarioId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{membershipId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(etag(response.version()))
                .body(response);
    }

    @GetMapping("/{membershipId}")
    public ResponseEntity<MembershipResponse> buscar(
            @PathVariable Long organizationId,
            @PathVariable Long membershipId) {
        MembershipResponse response = service.buscar(organizationId, membershipId);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PatchMapping("/{membershipId}")
    public ResponseEntity<MembershipResponse> atualizar(
            @PathVariable Long organizationId,
            @PathVariable Long membershipId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody AtualizarMembershipRequest request) {
        MembershipResponse response = service.atualizar(
                organizationId, membershipId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
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
