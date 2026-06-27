package com.kaneko.estacionamento.conveniencia;

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
 * Teste de integracao do CRUD de Marca — cobre todos os fluxos: criar, listar,
 * buscar (existente e inexistente), atualizar (existente e inexistente),
 * excluir e os erros de validacao e de unicidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarcaControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\" }";
        String body = mvc.perform(post("/api/marcas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Coca-Cola" }
                """;
        String body = mvc.perform(post("/api/marcas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Coca-Cola"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/marcas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Coca-Cola"));
    }

    @Test
    void listaMarcas() throws Exception {
        criar("Coca-Cola");
        criar("Pepsi");
        mvc.perform(get("/api/marcas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Coca-Cola");
        criar("Pepsi");

        mvc.perform(get("/api/marcas").param("nome", "coca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Coca-Cola"));
    }

    @Test
    void atualizaMarca() throws Exception {
        Number id = criar("Coca");
        String json = """
                { "nome": "Coca-Cola" }
                """;
        mvc.perform(put("/api/marcas/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Coca-Cola"));
    }

    @Test
    void atualizaMarcaInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/marcas/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiMarcaEdepoisRetorna404() throws Exception {
        Number id = criar("Temporaria");
        mvc.perform(delete("/api/marcas/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/marcas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Nestle");
        mvc.perform(post("/api/marcas")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Nestle\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/marcas/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeVazio() throws Exception {
        mvc.perform(post("/api/marcas")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }
}
