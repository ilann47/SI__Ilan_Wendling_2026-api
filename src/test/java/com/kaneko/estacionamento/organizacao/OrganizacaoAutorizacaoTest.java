package com.kaneko.estacionamento.organizacao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.security.JwtService;
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
class OrganizacaoAutorizacaoTest {

    private static final String CRIACAO = """
            {
              "document": "12.345.678/0001-95",
              "legalName": "Kaneko Estacionamentos Ltda",
              "currency": "BRL",
              "timeZone": "America/Sao_Paulo",
              "region": "BR-PR",
              "plan": "ENTERPRISE"
            }
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private JwtService jwtService;

    private String tokenAdmin;
    private String tokenOperador;
    private Usuario admin;

    @BeforeEach
    void setup() {
        admin = usuarioService.criar(new UsuarioRequest("Admin Organizacao", "org_admin", "senha123",
                null, null, PerfilUsuario.ADMIN));
        usuarioService.criar(new UsuarioRequest("Operador Organizacao", "org_oper", "senha123",
                null, null, PerfilUsuario.OPERADOR));
        tokenAdmin = "Bearer " + jwtService.gerarToken("org_admin", "ADMIN");
        tokenOperador = "Bearer " + jwtService.gerarToken("org_oper", "OPERADOR");
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/v1/organizations/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operadorRecebe403() throws Exception {
        mvc.perform(post("/api/v1/organizations")
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON).content(CRIACAO))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPodeCriarOrganizacao() throws Exception {
        mvc.perform(post("/api/v1/organizations")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(CRIACAO))
                .andExpect(status().isCreated());
    }

    @Test
    void adminPodeCriarOutraOrganizacaoMesmoComContextoAtivo() throws Exception {
        String organizacaoCriada = mvc.perform(post("/api/v1/organizations")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(CRIACAO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number organizacaoId = JsonPath.read(organizacaoCriada, "$.id");

        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId.longValue())
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + admin.getId() + "}"))
                .andExpect(status().isCreated());

        String contexto = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId.longValue() + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenContextual = "Bearer " + JsonPath.read(contexto, "$.token");

        mvc.perform(post("/api/v1/organizations")
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CRIACAO.replace("12.345.678/0001-95", "45.723.174/0001-10")))
                .andExpect(status().isCreated());
    }
}
