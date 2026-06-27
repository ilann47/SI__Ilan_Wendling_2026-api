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
 * Teste de integracao do CRUD de Categoria — cobre todos os fluxos: criar,
 * listar, buscar (existente e inexistente), atualizar (existente e
 * inexistente), excluir e os erros de validacao e de unicidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoriaControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\" }";
        String body = mvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Bebidas", "descricao": "Refrigerantes e sucos" }
                """;
        String body = mvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Bebidas"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/categorias/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Bebidas"))
                .andExpect(jsonPath("$.descricao").value("Refrigerantes e sucos"));
    }

    @Test
    void listaCategorias() throws Exception {
        criar("Bebidas");
        criar("Snacks");
        mvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Bebidas");
        criar("Snacks");

        mvc.perform(get("/api/categorias").param("nome", "bebid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Bebidas"));
    }

    @Test
    void atualizaCategoria() throws Exception {
        Number id = criar("Bebida");
        String json = """
                { "nome": "Bebidas", "descricao": "Linha completa" }
                """;
        mvc.perform(put("/api/categorias/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Bebidas"))
                .andExpect(jsonPath("$.descricao").value("Linha completa"));
    }

    @Test
    void atualizaCategoriaInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/categorias/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiCategoriaEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        mvc.perform(delete("/api/categorias/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/categorias/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Limpeza");
        mvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Limpeza\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/categorias/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeVazio() throws Exception {
        mvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }
}
