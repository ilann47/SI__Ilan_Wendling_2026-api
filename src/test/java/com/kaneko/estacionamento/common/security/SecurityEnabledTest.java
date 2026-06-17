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
@SpringBootTest(properties = "app.security.enabled=true")
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
        mvc.perform(get("/api/clientes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
