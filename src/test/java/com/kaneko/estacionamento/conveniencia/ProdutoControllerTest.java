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
 * Teste de integracao do CRUD de Produto — todos os fluxos, com as FKs de marca
 * e unidade de medida.
 */
@TenantApiIntegrationTest
@Transactional
class ProdutoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MarcaRepository marcaRepository;

    @Autowired
    private UnidadeMedidaRepository unidadeMedidaRepository;

    @Autowired
    private TenantTestSupport tenant;

    private Long marcaId;
    private Long unidadeId;
    private TenantFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
        Marca marca = new Marca();
        marca.setNome("Coca-Cola");
        marca.setOrganizacaoId(fixture.organizacaoId());
        marcaId = marcaRepository.save(marca).getId();

        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setNome("Unidade");
        unidade.setSigla("UN");
        unidade.setOrganizacaoId(fixture.organizacaoId());
        unidadeId = unidadeMedidaRepository.save(unidade).getId();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()));
    }

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"marcaId\": " + marcaId
                + ", \"unidadeMedidaId\": " + unidadeId + " }";
        String body = perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"nome\": \"Refrigerante Lata\", \"codigoBarras\": \"7891000\", \"marcaId\": " + marcaId
                + ", \"unidadeMedidaId\": " + unidadeId + ", \"valorVenda\": 5.50 }";
        String body = perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Refrigerante Lata"))
                .andExpect(jsonPath("$.marcaNome").value("Coca-Cola"))
                .andExpect(jsonPath("$.unidadeMedidaSigla").value("UN"))
                .andExpect(jsonPath("$.valorVenda").value(5.50))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        perform(get("/api/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoBarras").value("7891000"));
    }

    @Test
    void listaProdutos() throws Exception {
        criar("Produto Um");
        criar("Produto Dois");
        perform(get("/api/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Refrigerante Lata");
        criar("Agua Mineral");

        perform(get("/api/produtos").param("nome", "refrigerante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Refrigerante Lata"));
    }

    @Test
    void atualizaProduto() throws Exception {
        Number id = criar("Produto Antigo");
        String json = "{ \"nome\": \"Produto Novo\", \"marcaId\": " + marcaId
                + ", \"unidadeMedidaId\": " + unidadeId + ", \"valorVenda\": 9.90 }";
        perform(put("/api/produtos/" + id).header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Produto Novo"))
                .andExpect(jsonPath("$.valorVenda").value(9.90));
    }

    @Test
    void atualizaProdutoInexistenteRetorna404() throws Exception {
        String json = "{ \"nome\": \"X\", \"marcaId\": " + marcaId + ", \"unidadeMedidaId\": " + unidadeId + " }";
        perform(put("/api/produtos/999999").header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiProdutoEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        perform(delete("/api/produtos/" + id).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());
        perform(get("/api/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaCodigoBarrasDuplicadoCom409() throws Exception {
        String json = "{ \"nome\": \"Produto A\", \"codigoBarras\": \"99999999\", \"marcaId\": " + marcaId
                + ", \"unidadeMedidaId\": " + unidadeId + " }";
        perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoMarcaNaoExiste() throws Exception {
        String json = "{ \"nome\": \"Sem Marca\", \"marcaId\": 999999, \"unidadeMedidaId\": " + unidadeId + " }";
        perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        perform(get("/api/produtos/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoMarcaAusente() throws Exception {
        String json = "{ \"nome\": \"Sem Marca\", \"unidadeMedidaId\": " + unidadeId + " }";
        perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }
}
