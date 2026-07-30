package com.kaneko.estacionamento.membership;

import com.kaneko.estacionamento.common.security.ContextoAutenticadoService;
import com.kaneko.estacionamento.common.security.ContextoOrganizacao;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.membership.dto.MeResponse;
import com.kaneko.estacionamento.membership.dto.OrganizacaoAcessivelResponse;
import com.kaneko.estacionamento.membership.dto.SelecionarOrganizacaoRequest;
import com.kaneko.estacionamento.membership.dto.TokenContextoResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ContextoAutenticadoService contextoService;
    private final JwtService jwtService;

    public MeController(ContextoAutenticadoService contextoService, JwtService jwtService) {
        this.contextoService = contextoService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return MeResponse.from(principal);
    }

    @GetMapping("/organizations")
    public List<OrganizacaoAcessivelResponse> organizacoes(
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return contextoService.listarOrganizacoes(principal.userId());
    }

    @PostMapping("/active-organization")
    public TokenContextoResponse selecionarOrganizacao(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody SelecionarOrganizacaoRequest request) {
        ContextoOrganizacao contexto = contextoService.selecionar(
                principal.userId(), request.getOrganizacaoId());
        String token = jwtService.gerarTokenContextual(principal, contexto);
        long expiraEm = jwtService.validar(token).expiraEm();
        return TokenContextoResponse.of(
                token,
                OffsetDateTime.ofInstant(Instant.ofEpochSecond(expiraEm), ZoneOffset.UTC),
                contexto);
    }
}
