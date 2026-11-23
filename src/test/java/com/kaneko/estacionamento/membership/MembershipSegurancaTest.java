package com.kaneko.estacionamento.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.security.ContextoOrganizacao;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Transactional
class MembershipSegurancaTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private OrganizacaoService organizacaoService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OrganizacaoRepository organizacaoRepository;

    private Usuario admin;
    private Usuario operador;
    private Long organizacaoId;
    private String tokenAdmin;
    private String tokenOperador;

    @BeforeEach
    void setup() {
        admin = criarUsuario("Admin Membership", "member_admin", PerfilUsuario.ADMIN);
        operador = criarUsuario("Operador Membership", "member_oper", PerfilUsuario.OPERADOR);
        organizacaoId = organizacaoService.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Seguranca Ltda", "Kaneko Seguranca",
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        tokenAdmin = bearer(jwtService.gerarToken(admin.getLogin(), "OPERADOR"));
        tokenOperador = bearer(jwtService.gerarToken(operador.getLogin(), "ADMIN"));
    }

    @Test
    void authorityLegadaVemDoPerfilAtualDoBanco() throws Exception {
        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + operador.getId() + "}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + admin.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listaSomenteOrganizacoesAtivasComMembershipAtivaESelecionaContexto() throws Exception {
        membershipService.criar(organizacaoId, operador.getId());

        mvc.perform(get("/api/v1/me/organizations").header("Authorization", tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizationId").value(organizacaoId))
                .andExpect(jsonPath("$[0].membershipId").exists());

        String body = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.activeOrganization.organizationId").value(organizacaoId))
                .andReturn().getResponse().getContentAsString();
        String tokenContextual = JsonPath.read(body, "$.token");
        String contextual = bearer(tokenContextual);
        JwtService.Claims claims = jwtService.validar(tokenContextual);
        assertThat(claims.organizacaoId()).isEqualTo(organizacaoId);
        assertThat(claims.membershipId()).isNotNull();
        assertThat(claims.membershipVersion()).isZero();
        assertThat(claims.issuer()).isEqualTo("estacionamento-kaneko");
        assertThat(claims.audience()).isEqualTo("estacionamento-kaneko-api");
        assertThat(claims.jti()).isNotBlank();

        mvc.perform(get("/api/v1/me")
                        .header("Authorization", contextual)
                        .header("X-Organization-Id", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Operador Membership"))
                .andExpect(jsonPath("$.perfil").value("OPERADOR"))
                .andExpect(jsonPath("$.activeOrganization.organizationId").value(organizacaoId));
    }

    @Test
    void recusaSelecaoDeOrganizacaoSemMembershipERejeitaClaimsControladasPeloCliente() throws Exception {
        mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + "}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + ",\"membershipId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspensaoEReativacaoInvalidamTokenContextualAnteriorCom401() throws Exception {
        membershipService.criar(organizacaoId, admin.getId());
        String bodyAdmin = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String adminContextual = bearer(JsonPath.read(bodyAdmin, "$.token"));
        var membro = membershipService.criar(organizacaoId, operador.getId());
        String body = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String contextual = bearer(JsonPath.read(body, "$.token"));

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membro.id())
                        .header("Authorization", adminContextual)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENSO\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization", contextual))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membro.id())
                        .header("Authorization", adminContextual)
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ATIVO\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization", contextual))
                .andExpect(status().isUnauthorized());

        String tokenReativado = JsonPath.read(mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membro.id())
                        .header("Authorization", adminContextual)
                        .header("If-Match", "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENCERRADO\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(tokenReativado)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contextoDeOutroUsuarioEOrganizacaoSuspensaSaoInvalidos() throws Exception {
        var membro = membershipService.criar(organizacaoId, operador.getId());
        ContextoOrganizacao contexto = new ContextoOrganizacao(
                organizacaoId, membro.id(), membro.version());
        UsuarioAutenticado principalAdmin = new UsuarioAutenticado(
                admin.getId(), admin.getLogin(), admin.getNome(), admin.getEmail(),
                admin.getPerfil(), null, null, null);
        String tokenUsuarioErrado = jwtService.gerarTokenContextual(principalAdmin, contexto);

        mvc.perform(get("/api/v1/me").header("Authorization", bearer(tokenUsuarioErrado)))
                .andExpect(status().isUnauthorized());

        String tokenCorreto = JsonPath.read(mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");
        var organizacao = organizacaoRepository.findById(organizacaoId).orElseThrow();
        organizacao.suspender();
        organizacaoRepository.saveAndFlush(organizacao);

        mvc.perform(get("/api/v1/me").header("Authorization", bearer(tokenCorreto)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/organizations").header("Authorization", tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void tokenLegadoContinuaSemOrganizacaoAtiva() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(operador.getId()))
                .andExpect(jsonPath("$.nome").value("Operador Membership"))
                .andExpect(jsonPath("$.perfil").value("OPERADOR"))
                .andExpect(jsonPath("$.activeOrganization").doesNotExist());
    }

    private Usuario criarUsuario(String nome, String login, PerfilUsuario perfil) {
        return usuarioService.criar(new UsuarioRequest(nome, login, "senha123", null, null, perfil));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
