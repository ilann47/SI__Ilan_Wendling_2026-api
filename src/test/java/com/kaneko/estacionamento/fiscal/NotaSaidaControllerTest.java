package com.kaneko.estacionamento.fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.conveniencia.Marca;
import com.kaneko.estacionamento.conveniencia.MarcaRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.conveniencia.UnidadeMedida;
import com.kaneko.estacionamento.conveniencia.UnidadeMedidaRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao da nota de saida — criacao, confirmacao com validacao e
 * baixa de estoque, geracao de titulos e demais fluxos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotaSaidaControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private MarcaRepository marcaRepository;
    @Autowired
    private UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired
    private ProdutoRepository produtoRepository;

    private Long clienteId;
    private Long produtoId;

    @BeforeEach
    void setup() {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente A");
        cliente.setDocumento("12345678901");
        clienteId = clienteRepository.save(cliente).getId();

        Marca marca = new Marca();
        marca.setNome("Marca Z");
        marca = marcaRepository.save(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setNome("Unidade");
        unidade.setSigla("UN");
        unidade = unidadeMedidaRepository.save(unidade);
        Produto produto = new Produto();
        produto.setNome("Salgadinho");
        produto.setMarca(marca);
        produto.setUnidadeMedida(unidade);
        produto.setQuantidade(new BigDecimal("100.000"));
        produtoId = produtoRepository.save(produto).getId();
    }

    private String corpo(String numero, String quantidade) {
        return "{ \"numero\": \"" + numero + "\", \"clienteId\": " + clienteId
                + ", \"itens\": [ { \"produtoId\": " + produtoId
                + ", \"quantidade\": " + quantidade + ", \"valorUnitario\": 8.00 } ] }";
    }

    private Number criar(String numero, String quantidade) throws Exception {
        String body = mvc.perform(post("/api/notas-saida")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(numero, quantidade)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void rejeitaNotaParaClienteInativo() throws Exception {
        Cliente cliente = clienteRepository.findById(clienteId).orElseThrow();
        cliente.setAtivo(false);
        clienteRepository.save(cliente);
        mvc.perform(post("/api/notas-saida")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("VDINA", "1")))
                .andExpect(status().isConflict());
    }

    @Test
    void criaNotaPendenteComTotais() throws Exception {
        Number id = criar("VD1", "5");
        mvc.perform(get("/api/notas-saida/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PENDENTE"))
                .andExpect(jsonPath("$.valorProdutos").value(40.00))
                .andExpect(jsonPath("$.valorTotal").value(40.00))
                .andExpect(jsonPath("$.itens[0].valorTotal").value(40.00));
    }

    @Test
    void confirmaBaixaEstoqueEGeraTitulo() throws Exception {
        Number id = criar("VD2", "5");
        mvc.perform(post("/api/notas-saida/" + id + "/confirmacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONFIRMADA"));

        BigDecimal estoque = produtoRepository.findById(produtoId).orElseThrow().getQuantidade();
        assertEquals(0, estoque.compareTo(new BigDecimal("95")), "estoque deve baixar a quantidade vendida");

        mvc.perform(get("/api/contas-receber"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejeitaConfirmacaoSemEstoqueSuficiente() throws Exception {
        Number id = criar("VD3", "200");
        mvc.perform(post("/api/notas-saida/" + id + "/confirmacao"))
                .andExpect(status().isConflict());
        BigDecimal estoque = produtoRepository.findById(produtoId).orElseThrow().getQuantidade();
        assertEquals(0, estoque.compareTo(new BigDecimal("100")), "estoque não pode ser alterado quando a venda falha");
    }

    @Test
    void naoConfirmaNotaJaConfirmada() throws Exception {
        Number id = criar("VD4", "5");
        mvc.perform(post("/api/notas-saida/" + id + "/confirmacao")).andExpect(status().isOk());
        mvc.perform(post("/api/notas-saida/" + id + "/confirmacao")).andExpect(status().isConflict());
    }

    @Test
    void listaNotas() throws Exception {
        criar("VD5", "1");
        mvc.perform(get("/api/notas-saida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cancelaNotaPendente() throws Exception {
        Number id = criar("VD6", "1");
        mvc.perform(post("/api/notas-saida/" + id + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"));
    }

    @Test
    void excluiNotaPendenteEdepoisRetorna404() throws Exception {
        Number id = criar("VD7", "1");
        mvc.perform(delete("/api/notas-saida/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notas-saida/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void naoExcluiNotaConfirmada() throws Exception {
        Number id = criar("VD8", "1");
        mvc.perform(post("/api/notas-saida/" + id + "/confirmacao")).andExpect(status().isOk());
        mvc.perform(delete("/api/notas-saida/" + id)).andExpect(status().isConflict());
    }

    @Test
    void rejeitaNumeroDuplicado() throws Exception {
        criar("VD9", "1");
        mvc.perform(post("/api/notas-saida")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("VD9", "1")))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/notas-saida/999999")).andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoSemItens() throws Exception {
        String json = "{ \"numero\": \"VDX\", \"clienteId\": " + clienteId + ", \"itens\": [] }";
        mvc.perform(post("/api/notas-saida")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }
}
