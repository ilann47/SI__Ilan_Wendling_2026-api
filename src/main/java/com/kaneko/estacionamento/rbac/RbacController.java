package com.kaneko.estacionamento.rbac;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.rbac.dto.*;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RbacController {
    private static final Pattern ETAG = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");
    private final RbacService service;
    public RbacController(RbacService service) { this.service = service; }

    @GetMapping("/me/permissions")
    @PreAuthorize("@rbacGuard.hasActiveContext(authentication)")
    public PermissoesEfetivasResponse permissoes(@AuthenticationPrincipal UsuarioAutenticado p) {
        return new PermissoesEfetivasResponse(p.organizacaoId(),
                p.possuiOrganizacaoAtiva() ? service.permissoes(p.membershipId(), p.organizacaoId()) : java.util.List.of());
    }

    @PostMapping("/organizations/{organizationId}/memberships/{membershipId}/role-assignments")
    @PreAuthorize("@rbacGuard.hasPermission(#organizationId, 'roles:grant', authentication)")
    public ResponseEntity<AtribuicaoAcessoResponse> conceder(@PathVariable Long organizationId,
            @PathVariable Long membershipId, @Valid @RequestBody ConcederPapelRequest request) {
        var r = service.conceder(membershipId, organizationId, request.getRoleCode(),
                request.getValidFrom(), request.getValidUntil());
        return ResponseEntity.status(HttpStatus.CREATED).eTag("\"" + r.version() + "\"").body(r);
    }

    @DeleteMapping("/organizations/{organizationId}/memberships/{membershipId}/role-assignments/{assignmentId}")
    @PreAuthorize("@rbacGuard.hasPermission(#organizationId, 'roles:revoke', authentication)")
    public ResponseEntity<Void> revogar(@PathVariable Long organizationId, @PathVariable Long membershipId,
            @PathVariable Long assignmentId, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody RevogarPapelRequest request) {
        var m = ETAG.matcher(ifMatch); if (!m.matches()) throw new IllegalArgumentException("If-Match invalido.");
        service.revogar(membershipId, organizationId, assignmentId, Long.parseLong(m.group(1)), request.reason());
        return ResponseEntity.noContent().build();
    }
}
