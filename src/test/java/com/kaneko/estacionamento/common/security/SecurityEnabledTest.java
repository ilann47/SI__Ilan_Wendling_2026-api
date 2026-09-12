package com.kaneko.estacionamento.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
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

/**
 * Teste da protecao efetiva: com {@code app.security.enabled=true}, os endpoints
 * /api/** exigem token JWT. Valida 401 sem token e 200 com token valido.
 */
@SpringBootTest(properties = {
        "app.security.enabled=true",
        "management.endpoints.web.exposure.include=health,info,metrics"
})
@AutoConfigureMockMvc
@Transactional
class SecurityEnabledTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarioService;

    @BeforeEach
    void setup() {
        usuarioService.criar(new UsuarioRequest("Operador", "operador", "senha123",
                null, null, PerfilUsuario.OPERADOR));
    }

    @Test
    void acessoSemTokenRetorna401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void acessoComTokenInvalidoRetorna401() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorExpoeSaudeMasProtegeMetricas() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());

        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"operador\", \"senha\": \"senha123\" }"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        mvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void loginEhPublicoEAcessoComTokenRetorna200() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"operador\", \"senha\": \"senha123\" }"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        // /api/usuarios e ADMIN-only; o operador prova o token contra uma rota
        // acessivel a ele (cadastro geral). A autorizacao por perfil tem cobertura
        // dedicada em AutorizacaoPerfilTest.
        mvc.perform(get("/api/paises").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void cadastroEhPublicoMasContaNeutraNaoRecebePrivilegioAdministrativo() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Conta Publica",
                                  "login": "conta.publica",
                                  "email": "conta.publica@example.com",
                                  "senha": "senha-segura-123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.token");
        mvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/organizations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("[]"));
        mvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/paises")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
