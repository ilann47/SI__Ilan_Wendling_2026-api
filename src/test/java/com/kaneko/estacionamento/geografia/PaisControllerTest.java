package com.kaneko.estacionamento.geografia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do CRUD de Pais. Sobe o contexto em modo MOCK (sem
 * servidor real), com o Flyway aplicando o schema no banco de teste. Cada
 * metodo roda em transacao com rollback ({@link Transactional}), nao deixando
 * residuo no banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaisControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Brasil", "sigla": "BRA", "nacionalidade": "Brasileiro" }
                """;
        String body = mvc.perform(post("/api/paises")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Brasil"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/paises/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Brasil"))
                .andExpect(jsonPath("$.sigla").value("BRA"));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        String json = """
                { "nome": "Argentina" }
                """;
        mvc.perform(post("/api/paises")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/paises")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/paises/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeVazio() throws Exception {
        mvc.perform(post("/api/paises")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }
}
