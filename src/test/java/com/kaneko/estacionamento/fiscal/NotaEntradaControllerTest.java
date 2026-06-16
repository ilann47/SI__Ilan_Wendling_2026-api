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
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao da nota de entrada — criacao com calculo de totais e
 * rateio, confirmacao (estoque + titulos) e demais fluxos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotaEntradaControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FornecedorRepository fornecedorRepository;
    @Autowired
    private MarcaRepository marcaRepository;
    @Autowired
    private UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private CondicaoPagamentoRepository condicaoPagamentoRepository;

    private Long fornecedorId;
    private Long produtoId;
    private Long condicaoId;

    @BeforeEach
    void setup() {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setNome("Distribuidora X");
        fornecedor.setDocumento("11222333000144");
        fornecedorId = fornecedorRepository.save(fornecedor).getId();

        Marca marca = new Marca();
        marca.setNome("Marca Y");
        marca = marcaRepository.save(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setNome("Unidade");
        unidade.setSigla("UN");
        unidade = unidadeMedidaRepository.save(unidade);
        Produto produto = new Produto();
        produto.setNome("Refrigerante");
        produto.setMarca(marca);
        produto.setUnidadeMedida(unidade);
        produtoId = produtoRepository.save(produto).getId();

        CondicaoPagamento cond = new CondicaoPagamento();
        cond.setNome("30/60");
        cond.setNumeroParcelas(2);
        ParcelaCondicaoPagamento p1 = new ParcelaCondicaoPagamento();
        p1.setNumero(1);
        p1.setDias(30);
        p1.setPercentual(new BigDecimal("50.00"));
        ParcelaCondicaoPagamento p2 = new ParcelaCondicaoPagamento();
        p2.setNumero(2);
        p2.setDias(60);
        p2.setPercentual(new BigDecimal("50.00"));
        cond.setParcelas(List.of(p1, p2));
        condicaoId = condicaoPagamentoRepository.save(cond).getId();
    }

    private String corpo(String numero, String freteJson, Long condId) {
        String cond = condId != null ? ", \"condicaoPagamentoId\": " + condId : "";
        String frete = freteJson != null ? ", \"valorFrete\": " + freteJson : "";
        return "{ \"numero\": \"" + numero + "\", \"fornecedorId\": " + fornecedorId + cond + frete
                + ", \"itens\": [ { \"produtoId\": " + produtoId
                + ", \"quantidade\": 10, \"valorUnitario\": 5.00 } ] }";
    }

    private Number criar(String numero, String freteJson, Long condId) throws Exception {
        String body = mvc.perform(post("/api/notas-entrada")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(numero, freteJson, condId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaNotaPendenteComTotaisCalculados() throws Exception {
        Number id = criar("NF1", null, null);
        mvc.perform(get("/api/notas-entrada/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PENDENTE"))
                .andExpect(jsonPath("$.valorProdutos").value(50.00))
                .andExpect(jsonPath("$.valorTotal").value(50.00))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].valorTotal").value(50.00));
    }

    @Test
    void rateiaFreteNosItensEAtualizaCusto() throws Exception {
        Number id = criar("NF2", "10.00", null);
        mvc.perform(get("/api/notas-entrada/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(60.00))
                .andExpect(jsonPath("$.itens[0].rateioFrete").value(10.00))
                .andExpect(jsonPath("$.itens[0].custoPrecoFinal").value(6.00));
    }

    @Test
    void confirmaSomaEstoqueEGeraTitulos() throws Exception {
        Number id = criar("NF3", null, condicaoId);
        mvc.perform(post("/api/notas-entrada/" + id + "/confirmacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONFIRMADA"));

        BigDecimal estoque = produtoRepository.findById(produtoId).orElseThrow().getQuantidade();
        assertEquals(0, estoque.compareTo(new BigDecimal("10")), "estoque deve somar a quantidade comprada");

        mvc.perform(get("/api/contas-pagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void naoConfirmaNotaJaConfirmada() throws Exception {
        Number id = criar("NF4", null, null);
        mvc.perform(post("/api/notas-entrada/" + id + "/confirmacao")).andExpect(status().isOk());
        mvc.perform(post("/api/notas-entrada/" + id + "/confirmacao")).andExpect(status().isConflict());
    }

    @Test
    void listaNotas() throws Exception {
        criar("NF5", null, null);
        mvc.perform(get("/api/notas-entrada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cancelaNotaPendente() throws Exception {
        Number id = criar("NF6", null, null);
        mvc.perform(post("/api/notas-entrada/" + id + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"));
    }

    @Test
    void excluiNotaPendenteEdepoisRetorna404() throws Exception {
        Number id = criar("NF7", null, null);
        mvc.perform(delete("/api/notas-entrada/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notas-entrada/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void naoExcluiNotaConfirmada() throws Exception {
        Number id = criar("NF8", null, null);
        mvc.perform(post("/api/notas-entrada/" + id + "/confirmacao")).andExpect(status().isOk());
        mvc.perform(delete("/api/notas-entrada/" + id)).andExpect(status().isConflict());
    }

    @Test
    void rejeitaNumeroDuplicado() throws Exception {
        criar("NF9", null, null);
        mvc.perform(post("/api/notas-entrada")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("NF9", null, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/notas-entrada/999999")).andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoSemItens() throws Exception {
        String json = "{ \"numero\": \"NFX\", \"fornecedorId\": " + fornecedorId + ", \"itens\": [] }";
        mvc.perform(post("/api/notas-entrada")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }
}
