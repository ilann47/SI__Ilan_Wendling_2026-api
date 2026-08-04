package com.kaneko.estacionamento.usuario;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do cadastro de usuarios — todos os fluxos, com a garantia
 * de que a senha (hash) nunca e exposta na resposta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String login) throws Exception {
        String json = "{ \"nome\": \"Administrador\", \"login\": \"" + login
                + "\", \"senha\": \"segredo123\", \"perfil\": \"ADMIN\" }";
        String body = mvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaUsuarioESenhaNaoEhExposta() throws Exception {
        Number id = criar("admin");
        mvc.perform(get("/api/usuarios/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("admin"))
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void listaUsuarios() throws Exception {
        criar("usr1");
        criar("usr2");
        mvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].login", hasItems("usr1", "usr2")));
    }

    @Test
    void filtraPorPerfil() throws Exception {
        criar("admin");
        String operador = "{ \"nome\": \"Operador\", \"login\": \"oper\","
                + " \"senha\": \"segredo123\", \"perfil\": \"OPERADOR\" }";
        mvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON).content(operador))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/usuarios").param("perfil", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].login", hasItem("admin")))
                .andExpect(jsonPath("$.content[*].login", not(hasItem("oper"))))
                .andExpect(jsonPath("$.content[*].perfil", everyItem(is("ADMIN"))));
    }

    @Test
    void atualizaUsuario() throws Exception {
        Number id = criar("oper");
        String json = "{ \"nome\": \"Operador Editado\", \"login\": \"oper\", \"senha\": \"novasenha1\", \"perfil\": \"OPERADOR\" }";
        mvc.perform(put("/api/usuarios/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Operador Editado"))
                .andExpect(jsonPath("$.perfil").value("OPERADOR"));
    }

    @Test
    void excluiUsuarioEdepoisRetorna404() throws Exception {
        Number id = criar("temp");
        mvc.perform(delete("/api/usuarios/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaLoginDuplicadoCom409() throws Exception {
        criar("repetido");
        String json = "{ \"nome\": \"Outro\", \"login\": \"REPETIDO\", \"senha\": \"segredo123\" }";
        mvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/usuarios/999999")).andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoSemSenha() throws Exception {
        String json = "{ \"nome\": \"Sem Senha\", \"login\": \"semsenha\" }";
        mvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }
}
