package com.kaneko.estacionamento.membership;

import com.kaneko.estacionamento.common.security.ContextoAutenticadoService;
import com.kaneko.estacionamento.common.security.ContextoOrganizacao;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.membership.dto.MeResponse;
import com.kaneko.estacionamento.membership.dto.OrganizacaoAcessivelResponse;
import com.kaneko.estacionamento.membership.dto.OrganizacaoPropriaResponse;
import com.kaneko.estacionamento.membership.dto.SelecionarOrganizacaoRequest;
import com.kaneko.estacionamento.membership.dto.TokenContextoResponse;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ContextoAutenticadoService contextoService;
    private final JwtService jwtService;
    private final OrganizacaoSelfServiceService organizacaoSelfService;

    public MeController(
            ContextoAutenticadoService contextoService,
            JwtService jwtService,
            OrganizacaoSelfServiceService organizacaoSelfService) {
        this.contextoService = contextoService;
        this.jwtService = jwtService;
        this.organizacaoSelfService = organizacaoSelfService;
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

    @PostMapping("/organizations")
    @PreAuthorize("@rbacGuard.canSelfProvisionOrganization(authentication)")
    @Operation(summary = "Criar organizacao propria",
            description = "Cria organizacao e Membership administrativa do usuario autenticado, "
                    + "mantendo o JWT atual sem troca automatica de tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Organizacao e Membership administrativa criadas"),
            @ApiResponse(responseCode = "400", description = "Dados empresariais invalidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido"),
            @ApiResponse(responseCode = "403", description = "Perfil global sem acesso ao self-service"),
            @ApiResponse(responseCode = "409", description = "Documento empresarial ja cadastrado")
    })
    public ResponseEntity<OrganizacaoPropriaResponse> criarOrganizacaoPropria(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody CriarOrganizacaoRequest request) {
        OrganizacaoPropriaResponse response = organizacaoSelfService.criar(principal.userId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/organizations/{id}")
                .buildAndExpand(response.organization().id())
                .toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.organization().version() + "\"")
                .body(response);
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
