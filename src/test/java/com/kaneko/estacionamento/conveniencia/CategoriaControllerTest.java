package com.kaneko.estacionamento.conveniencia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Teste de integracao do CRUD de Categoria — cobre todos os fluxos: criar,
 * listar, buscar (existente e inexistente), atualizar (existente e
 * inexistente), excluir e os erros de validacao e de unicidade.
 */
@TenantApiIntegrationTest
class CategoriaControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TenantTestSupport tenant;
    private TenantFixture tenantFixture;

    @BeforeEach
    void setupTenant() throws Exception {
        tenantFixture = tenant.criarTenantAdmin();
    }

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\" }";
        String body = perform(post("/api/categorias")
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
        String body = perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Bebidas"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        perform(get("/api/categorias/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Bebidas"))
                .andExpect(jsonPath("$.descricao").value("Refrigerantes e sucos"));
    }

    @Test
    void listaCategorias() throws Exception {
        criar("Bebidas");
        criar("Snacks");
        perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Bebidas");
        criar("Snacks");

        perform(get("/api/categorias").param("nome", "bebid"))
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
        perform(put("/api/categorias/" + id)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Bebidas"))
                .andExpect(jsonPath("$.descricao").value("Linha completa"));
    }

    @Test
    void atualizaCategoriaInexistenteRetorna404() throws Exception {
        perform(put("/api/categorias/999999")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiCategoriaEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        perform(delete("/api/categorias/" + id).header("If-Match", "\"0\""))
                .andExpect(status().isNoContent());
        perform(get("/api/categorias/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Limpeza");
        perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Limpeza\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        perform(get("/api/categorias/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeVazio() throws Exception {
        perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", tenantFixture.tokenContextual()));
    }
}
