package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.acesso.dto.PaginaTentativasAcessoResponse;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access-attempts")
public class TentativaAcessoController {
    private final ConsultaTentativaAcessoService service;

    public TentativaAcessoController(ConsultaTentativaAcessoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('audit:read', authentication)")
    public ResponseEntity<PaginaTentativasAcessoResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) StatusTentativaAcesso decision,
            @RequestParam(required = false) MotivoDecisaoAcesso reason,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(service.listar(
                principal.organizacaoId(), cursor, eventId,
                decision, reason, limit));
    }
}
