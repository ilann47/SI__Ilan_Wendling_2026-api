package com.kaneko.estacionamento.conveniencia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do CRUD de Produto — todos os fluxos, com as FKs de marca
 * e unidade de medida.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProdutoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MarcaRepository marcaRepository;

    @Autowired
    private UnidadeMedidaRepository unidadeMedidaRepository;

    private Long marcaId;
    private Long unidadeId;

    @BeforeEach
    void setup() {
        Marca marca = new Marca();
        marca.setNome("Coca-Cola");
        marcaId = marcaRepository.save(marca).getId();

        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setNome("Unidade");
        unidade.setSigla("UN");
        unidadeId = unidadeMedidaRepository.save(unidade).getId();
    }

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"marcaId\": " + marcaId
                + ", \"unidadeMedidaId\": " + unidadeId + " }";
        String body = mvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"nome\": \"Refrigerante Lata\", \"codigoBarras\": \"7891000\", \"marcaId\": " + marcaId
                + ", \"unidadeMedidaId\": " + unidadeId + ", \"valorVenda\": 5.50, \"quantidade\": 100 }";
        String body = mvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Refrigerante Lata"))
                .andExpect(jsonPath("$.marcaNome").value("Coca-Cola"))
                .andExpect(jsonPath("$.unidadeMedidaSigla").value("UN"))
                .andExpect(jsonPath("$.valorVenda").value(5.50))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoBarras").value("7891000"));
    }

    @Test
    void listaProdutos() throws Exception {
        criar("Produto Um");
        criar("Produto Dois");
        mvc.perform(get("/api/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void atualizaProduto() throws Exception {
        Number id = criar("Produto Antigo");
        String json = "{ \"nome\": \"Produto Novo\", \"marcaId\": " + marcaId
                + ", \"unidadeMedidaId\": " + unidadeId + ", \"valorVenda\": 9.90 }";
        mvc.perform(put("/api/produtos/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Produto Novo"))
                .andExpect(jsonPath("$.valorVenda").value(9.90));
    }

    @Test
    void atualizaProdutoInexistenteRetorna404() throws Exception {
        String json = "{ \"nome\": \"X\", \"marcaId\": " + marcaId + ", \"unidadeMedidaId\": " + unidadeId + " }";
        mvc.perform(put("/api/produtos/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiProdutoEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario");
        mvc.perform(delete("/api/produtos/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/produtos/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaCodigoBarrasDuplicadoCom409() throws Exception {
        String json = "{ \"nome\": \"Produto A\", \"codigoBarras\": \"99999999\", \"marcaId\": " + marcaId
                + ", \"unidadeMedidaId\": " + unidadeId + " }";
        mvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoMarcaNaoExiste() throws Exception {
        String json = "{ \"nome\": \"Sem Marca\", \"marcaId\": 999999, \"unidadeMedidaId\": " + unidadeId + " }";
        mvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/produtos/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoMarcaAusente() throws Exception {
        String json = "{ \"nome\": \"Sem Marca\", \"unidadeMedidaId\": " + unidadeId + " }";
        mvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }
}
