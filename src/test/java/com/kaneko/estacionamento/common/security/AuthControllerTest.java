package com.kaneko.estacionamento.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Teste de integracao da autenticacao — login valido emite token; credenciais
 * invalidas resultam em 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

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
    void loginValidoEmiteToken() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"operador\", \"senha\": \"senha123\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.perfil").value("OPERADOR"));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"operador\", \"senha\": \"errada\" }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginDeUsuarioInexistenteRetorna401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"naoexiste\", \"senha\": \"qualquer\" }"))
                .andExpect(status().isUnauthorized());
    }
}
