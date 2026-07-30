package com.kaneko.estacionamento.rbac;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.security.JwtService;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Transactional
class RbacContextualTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarioService;
    @Autowired OrganizacaoService organizacaoService;
    @Autowired JwtService jwtService;

    private Usuario adminPlataforma;
    private Usuario segundoUsuario;
    private Long organizacaoId;
    private String tokenAdminLegado;

    @BeforeEach
    void setup() {
        adminPlataforma = usuario("rbac_admin", PerfilUsuario.ADMIN);
        segundoUsuario = usuario("rbac_operador", PerfilUsuario.OPERADOR);
        organizacaoId = organizacaoService.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko RBAC Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        tokenAdminLegado = bearer(jwtService.gerarToken(adminPlataforma.getLogin(), "OPERADOR"));
    }

    @Test
    void primeiroMembershipRecebeAdminEPassaAAdministrarTenant() throws Exception {
        long primeiro = criarMembership(tokenAdminLegado, adminPlataforma.getId(), status().isCreated());
        String adminContextual = selecionar(tokenAdminLegado);

        mvc.perform(get("/api/v1/me/permissions").header("Authorization", adminContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@ == 'organizations:admin')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'users:invite')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'roles:grant')]").exists());

        criarMembership(adminContextual, segundoUsuario.getId(), status().isCreated());
        mvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, primeiro).header("Authorization", adminContextual))
                .andExpect(status().isOk());
    }

    @Test
    void adminGlobalSoPodeProvisionarPrimeiroMembership() throws Exception {
        criarMembership(tokenAdminLegado, adminPlataforma.getId(), status().isCreated());
        criarMembership(tokenAdminLegado, segundoUsuario.getId(), status().isForbidden());

        mvc.perform(get("/api/v1/me/permissions").header("Authorization", tokenAdminLegado))
                .andExpect(status().isForbidden());
    }

    @Test
    void contextoNaoRevelaRecursosDeOutraOrganizacao() throws Exception {
        criarMembership(tokenAdminLegado, adminPlataforma.getId(), status().isCreated());
        String contextual = selecionar(tokenAdminLegado);
        Long outraOrganizacao = organizacaoService.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Tenant RBAC Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();

        mvc.perform(get("/api/v1/organizations/{organizationId}", outraOrganizacao)
                        .header("Authorization", contextual))
                .andExpect(status().isNotFound());
    }

    @Test
    void concessaoERevogacaoAlteramPermissoesSemReemitirToken() throws Exception {
        criarMembership(tokenAdminLegado, adminPlataforma.getId(), status().isCreated());
        String adminContextual = selecionar(tokenAdminLegado);
        long membershipOperador = criarMembership(adminContextual, segundoUsuario.getId(), status().isCreated());
        String operadorContextual = selecionar(
                bearer(jwtService.gerarToken(segundoUsuario.getLogin(), "ADMIN")));

        mvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipOperador).header("Authorization", operadorContextual))
                .andExpect(status().isForbidden());

        String body = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments",
                        organizacaoId, membershipOperador)
                        .header("Authorization", adminContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"ADMIN_ORGANIZACAO\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andReturn().getResponse().getContentAsString();
        long assignmentId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipOperador).header("Authorization", operadorContextual))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments/{assignmentId}",
                        organizacaoId, membershipOperador, assignmentId)
                        .header("Authorization", adminContextual).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Acesso administrativo removido\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipOperador).header("Authorization", operadorContextual))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizacaoSoPodeSerAlteradaNoContextoComPermissao() throws Exception {
        criarMembership(tokenAdminLegado, adminPlataforma.getId(), status().isCreated());
        String contextual = selecionar(tokenAdminLegado);
        String payload = "{\"legalName\":\"Kaneko RBAC Atualizada Ltda\"}";

        mvc.perform(patch("/api/v1/organizations/{id}", organizacaoId)
                        .header("Authorization", tokenAdminLegado).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/organizations/{id}", organizacaoId)
                        .header("Authorization", contextual).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void impedeSuspenderOUltimoAdministradorDaOrganizacao() throws Exception {
        long membershipId = criarMembership(tokenAdminLegado, adminPlataforma.getId(), status().isCreated());
        String contextual = selecionar(tokenAdminLegado);
        mvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizacaoId, membershipId)
                        .header("Authorization", contextual).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENSO\"}"))
                .andExpect(status().isConflict());
    }

    private long criarMembership(String token, Long userId, ResultMatcher expected) throws Exception {
        var result = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + userId + "}"))
                .andExpect(expected).andReturn();
        if (result.getResponse().getStatus() != 201) return -1;
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String selecionar(String token) throws Exception {
        String body = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return bearer(JsonPath.read(body, "$.token"));
    }

    private Usuario usuario(String login, PerfilUsuario perfil) {
        return usuarioService.criar(new UsuarioRequest(
                "Usuario RBAC", login, "senha123", null, null, perfil));
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
