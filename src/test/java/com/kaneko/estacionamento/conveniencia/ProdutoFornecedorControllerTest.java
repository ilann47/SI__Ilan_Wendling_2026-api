package com.kaneko.estacionamento.conveniencia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do vinculo produto-fornecedor — todos os fluxos, com a
 * unicidade do par e as FKs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProdutoFornecedorControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MarcaRepository marcaRepository;

    @Autowired
    private UnidadeMedidaRepository unidadeMedidaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private FornecedorRepository fornecedorRepository;

    private Long produtoId;
    private Long fornecedorId;

    @BeforeEach
    void setup() {
        Marca marca = new Marca();
        marca.setNome("Marca X");
        marca = marcaRepository.save(marca);

        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setNome("Unidade");
        unidade.setSigla("UN");
        unidade = unidadeMedidaRepository.save(unidade);

        Produto produto = new Produto();
        produto.setNome("Produto X");
        produto.setMarca(marca);
        produto.setUnidadeMedida(unidade);
        produtoId = produtoRepository.save(produto).getId();

        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setNome("Fornecedor X");
        fornecedor.setDocumento("11222333000144");
        fornecedorId = fornecedorRepository.save(fornecedor).getId();
    }

    private Number criar() throws Exception {
        String json = "{ \"produtoId\": " + produtoId + ", \"fornecedorId\": " + fornecedorId
                + ", \"codigoProd\": \"ABC123\", \"custo\": 3.50 }";
        String body = mvc.perform(post("/api/produto-fornecedores")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        Number id = criar();
        mvc.perform(get("/api/produto-fornecedores/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtoNome").value("Produto X"))
                .andExpect(jsonPath("$.fornecedorNome").value("Fornecedor X"))
                .andExpect(jsonPath("$.custo").value(3.50));
    }

    @Test
    void listaVinculos() throws Exception {
        criar();
        mvc.perform(get("/api/produto-fornecedores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void atualizaVinculo() throws Exception {
        Number id = criar();
        String json = "{ \"produtoId\": " + produtoId + ", \"fornecedorId\": " + fornecedorId
                + ", \"codigoProd\": \"XYZ\", \"custo\": 4.00 }";
        mvc.perform(put("/api/produto-fornecedores/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoProd").value("XYZ"))
                .andExpect(jsonPath("$.custo").value(4.00));
    }

    @Test
    void atualizaVinculoInexistenteRetorna404() throws Exception {
        String json = "{ \"produtoId\": " + produtoId + ", \"fornecedorId\": " + fornecedorId + " }";
        mvc.perform(put("/api/produto-fornecedores/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiVinculoEdepoisRetorna404() throws Exception {
        Number id = criar();
        mvc.perform(delete("/api/produto-fornecedores/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/produto-fornecedores/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaParDuplicadoCom409() throws Exception {
        criar();
        String json = "{ \"produtoId\": " + produtoId + ", \"fornecedorId\": " + fornecedorId + " }";
        mvc.perform(post("/api/produto-fornecedores")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoProdutoNaoExiste() throws Exception {
        String json = "{ \"produtoId\": 999999, \"fornecedorId\": " + fornecedorId + " }";
        mvc.perform(post("/api/produto-fornecedores")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/produto-fornecedores/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoProdutoAusente() throws Exception {
        String json = "{ \"fornecedorId\": " + fornecedorId + " }";
        mvc.perform(post("/api/produto-fornecedores")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }
}
