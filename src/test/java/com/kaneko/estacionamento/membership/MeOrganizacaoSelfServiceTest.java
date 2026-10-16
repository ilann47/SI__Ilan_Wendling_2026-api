package com.kaneko.estacionamento.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
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
class MeOrganizacaoSelfServiceTest {

    private static final String ORGANIZACAO_UM = """
            {
              "document": "12.345.678/0001-95",
              "legalName": "Organizacao Propria Um Ltda",
              "tradeName": "Organizacao Um",
              "currency": "BRL",
              "timeZone": "America/Sao_Paulo",
              "region": "BR-SP",
              "plan": "ENTERPRISE"
            }
            """;

    private static final String ORGANIZACAO_DOIS = ORGANIZACAO_UM
            .replace("12.345.678/0001-95", "45.723.174/0001-10")
            .replace("Organizacao Propria Um Ltda", "Organizacao Propria Dois Ltda")
            .replace("Organizacao Um", "Organizacao Dois");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private JwtService jwtService;

    @Test
    void usuarioCriaOrganizacaoComMembershipAdminEAtivaSomenteQuandoEscolher() throws Exception {
        ContaRegistrada conta = registrar("selfserve.usuario", "selfserve.usuario@example.com");

        mvc.perform(get("/api/v1/me/organizations").header("Authorization", conta.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        String criacao = mvc.perform(post("/api/v1/me/organizations")
                        .header("Authorization", conta.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORGANIZACAO_UM))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.organization.status").value("ATIVA"))
                .andExpect(jsonPath("$.organization.document").value("12345678000195"))
                .andExpect(jsonPath("$.membership.userId").value(conta.userId()))
                .andExpect(jsonPath("$.membership.status").value("ATIVO"))
                .andReturn().getResponse().getContentAsString();
        Number organizacaoId = JsonPath.read(criacao, "$.organization.id");

        mvc.perform(get("/api/v1/me").header("Authorization", conta.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Usuario Self Service"))
                .andExpect(jsonPath("$.perfil").value("USUARIO"))
                .andExpect(jsonPath("$.activeOrganization").doesNotExist());
        mvc.perform(get("/api/v1/me/organizations").header("Authorization", conta.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizationId").value(organizacaoId.longValue()));

        String ativacao = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", conta.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId.longValue() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeOrganization.organizationId").value(organizacaoId.longValue()))
                .andReturn().getResponse().getContentAsString();
        String tokenContextual = JsonPath.read(ativacao, "$.token");
        assertThat(jwtService.validar(tokenContextual).organizacaoId()).isEqualTo(organizacaoId.longValue());

        mvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Usuario Self Service"))
                .andExpect(jsonPath("$.perfil").value("USUARIO"))
                .andExpect(jsonPath("$.activeOrganization.organizationId")
                        .value(organizacaoId.longValue()));

        mvc.perform(get("/api/v1/me/permissions")
                        .header("Authorization", "Bearer " + tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").value(
                        org.hamcrest.Matchers.hasItem("organizations:admin")));
    }

    @Test
    void usuarioPodeCriarOutraOrganizacaoMesmoComContextoAtivo() throws Exception {
        ContaRegistrada conta = registrar("selfserve.multiorg", "selfserve.multiorg@example.com");
        long primeiraId = criarOrganizacao(conta.authorization(), ORGANIZACAO_UM);
        String ativacao = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", conta.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + primeiraId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenContextual = "Bearer " + JsonPath.read(ativacao, "$.token");

        criarOrganizacao(tokenContextual, ORGANIZACAO_DOIS);

        mvc.perform(get("/api/v1/me/organizations").header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejeitaDocumentoDuplicadoComConflitoClaro() throws Exception {
        ContaRegistrada conta = registrar("selfserve.duplicado", "selfserve.duplicado@example.com");
        criarOrganizacao(conta.authorization(), ORGANIZACAO_UM);

        mvc.perform(post("/api/v1/me/organizations")
                        .header("Authorization", conta.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORGANIZACAO_UM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"))
                .andExpect(jsonPath("$.detail").value("Ja existe uma organizacao com este documento."))
                .andExpect(jsonPath("$.erros.document")
                        .value("Ja existe uma organizacao com este documento."));
    }

    @Test
    void operadorLegadoNaoPodeUsarSelfService() throws Exception {
        var operador = usuarioService.criar(new UsuarioRequest(
                "Operador sem self-service", "operador.sem.selfservice", "senha123",
                null, null, PerfilUsuario.OPERADOR));
        String token = "Bearer " + jwtService.gerarToken(operador);

        mvc.perform(post("/api/v1/me/organizations")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORGANIZACAO_UM))
                .andExpect(status().isForbidden());
    }

    @Test
    void exigeAutenticacaoEDevolveErrosDeValidacaoEstruturados() throws Exception {
        mvc.perform(post("/api/v1/me/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORGANIZACAO_UM))
                .andExpect(status().isUnauthorized());

        ContaRegistrada conta = registrar("selfserve.invalido", "selfserve.invalido@example.com");
        mvc.perform(post("/api/v1/me/organizations")
                        .header("Authorization", conta.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORGANIZACAO_UM.replace(
                                "\"legalName\": \"Organizacao Propria Um Ltda\"",
                                "\"legalName\": \"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erros.razaoSocial").exists());
    }

    @Test
    void openApiPublicaContratoSelfService() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/me/organizations'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/organizations'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/organizations'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.MeResponse.properties.nome").exists())
                .andExpect(jsonPath("$.components.schemas.MeResponse.properties.perfil").exists());
    }

    private ContaRegistrada registrar(String login, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Usuario Self Service",
                                  "login": "%s",
                                  "email": "%s",
                                  "senha": "senha-segura-123"
                                }
                                """.formatted(login, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("USUARIO"))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        String token = JsonPath.read(body, "$.token");
        return new ContaRegistrada(id.longValue(), "Bearer " + token);
    }

    private long criarOrganizacao(String authorization, String payload) throws Exception {
        String body = mvc.perform(post("/api/v1/me/organizations")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.organization.id");
        return id.longValue();
    }

    private record ContaRegistrada(long userId, String authorization) {
    }
}
