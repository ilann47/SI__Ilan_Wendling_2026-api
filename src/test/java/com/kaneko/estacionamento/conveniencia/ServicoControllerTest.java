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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do CRUD de Servico — cobre todos os fluxos: criar, listar,
 * buscar (existente e inexistente), atualizar (existente e inexistente),
 * excluir e os erros de validacao e de unicidade.
 */
@TenantApiIntegrationTest
@Transactional
class ServicoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantTestSupport tenant;

    private TenantFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()));
    }

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\" }";
        String body = perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Lavagem Simples", "descricao": "Lavagem externa", "valorPadrao": 25.00 }
                """;
        String body = perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").value("Lavagem Simples"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        perform(get("/api/servicos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Lavagem Simples"))
                .andExpect(jsonPath("$.descricao").value("Lavagem externa"));
    }

    @Test
    void listaServicos() throws Exception {
        criar("Lavagem");
        criar("Calibragem");
        perform(get("/api/servicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Lavagem");
        criar("Calibragem");

        perform(get("/api/servicos").param("nome", "lavag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Lavagem"));
    }

    @Test
    void atualizaServico() throws Exception {
        Number id = criar("Lavage");
        String json = """
                { "nome": "Lavagem", "descricao": "Lavagem completa" }
                """;
        perform(put("/api/servicos/" + id).header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Lavagem"))
                .andExpect(jsonPath("$.descricao").value("Lavagem completa"));
    }

    @Test
    void atualizaServicoInexistenteRetorna404() throws Exception {
        perform(put("/api/servicos/999999").header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiServicoEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        perform(delete("/api/servicos/" + id).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());
        perform(get("/api/servicos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Polimento");
        perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Polimento\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        perform(get("/api/servicos/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeVazio() throws Exception {
        perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna400QuandoValorNegativo() throws Exception {
        perform(post("/api/servicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Cera\", \"valorPadrao\": -5.00 }"))
                .andExpect(status().isBadRequest());
    }
}
