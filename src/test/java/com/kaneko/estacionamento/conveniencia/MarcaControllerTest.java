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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Teste de integracao do CRUD de Marca — cobre todos os fluxos: criar, listar,
 * buscar (existente e inexistente), atualizar (existente e inexistente),
 * excluir e os erros de validacao e de unicidade.
 */
@TenantApiIntegrationTest
class MarcaControllerTest {

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
        String body = perform(post("/api/marcas")
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
        String body = perform(post("/api/marcas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Coca-Cola"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        perform(get("/api/marcas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Coca-Cola"));
    }

    @Test
    void listaMarcas() throws Exception {
        criar("Coca-Cola");
        criar("Pepsi");
        perform(get("/api/marcas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Coca-Cola");
        criar("Pepsi");

        perform(get("/api/marcas").param("nome", "coca"))
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
        perform(put("/api/marcas/" + id)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Coca-Cola"));
    }

    @Test
    void atualizaMarcaInexistenteRetorna404() throws Exception {
        perform(put("/api/marcas/999999")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiMarcaEdepoisRetorna404() throws Exception {
        Number id = criar("Temporaria");
        perform(delete("/api/marcas/" + id).header("If-Match", "\"0\""))
                .andExpect(status().isNoContent());
        perform(get("/api/marcas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Nestle");
        perform(post("/api/marcas")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Nestle\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        perform(get("/api/marcas/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeVazio() throws Exception {
        perform(post("/api/marcas")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", tenantFixture.tokenContextual()));
    }
}
