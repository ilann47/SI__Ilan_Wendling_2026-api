package com.kaneko.estacionamento.financeiro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Teste de integracao do titulo a pagar — lancamento manual, consulta, baixa
 * parcial/total e cancelamento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContaPagarControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FornecedorRepository fornecedorRepository;

    private Long fornecedorId;

    @BeforeEach
    void setup() {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setNome("Fornecedor Z");
        fornecedor.setDocumento("99888777000166");
        fornecedorId = fornecedorRepository.save(fornecedor).getId();
    }

    private Number criar(String valorOriginal) throws Exception {
        String json = "{ \"fornecedorId\": " + fornecedorId + ", \"valorOriginal\": " + valorOriginal
                + ", \"dataVencimento\": \"2030-12-31\" }";
        String body = mvc.perform(post("/api/contas-pagar")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaTituloManualEbuscaPorId() throws Exception {
        Number id = criar("800.00");
        mvc.perform(get("/api/contas-pagar/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fornecedorNome").value("Fornecedor Z"))
                .andExpect(jsonPath("$.valorTotal").value(800.00))
                .andExpect(jsonPath("$.situacao").value("PENDENTE"));
    }

    @Test
    void listaTitulos() throws Exception {
        criar("100.00");
        criar("200.00");
        mvc.perform(get("/api/contas-pagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void baixaParcialMudaSituacaoParaParcial() throws Exception {
        Number id = criar("1000.00");
        mvc.perform(post("/api/contas-pagar/" + id + "/baixa")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"valor\": 400.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PARCIAL"))
                .andExpect(jsonPath("$.valorPago").value(400.00));
    }

    @Test
    void baixaTotalMudaSituacaoParaPaga() throws Exception {
        Number id = criar("500.00");
        mvc.perform(post("/api/contas-pagar/" + id + "/baixa")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"valor\": 500.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PAGA"))
                .andExpect(jsonPath("$.dataPagamento").isNotEmpty());
    }

    @Test
    void rejeitaBaixaQueExcedeSaldoCom409() throws Exception {
        Number id = criar("100.00");
        mvc.perform(post("/api/contas-pagar/" + id + "/baixa")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"valor\": 200.00 }"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelaTitulo() throws Exception {
        Number id = criar("50.00");
        mvc.perform(post("/api/contas-pagar/" + id + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"));
    }

    @Test
    void retorna404QuandoFornecedorNaoExiste() throws Exception {
        String json = "{ \"fornecedorId\": 999999, \"valorOriginal\": 100.00, \"dataVencimento\": \"2030-12-31\" }";
        mvc.perform(post("/api/contas-pagar")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/contas-pagar/999999")).andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoFornecedorOuValorAusente() throws Exception {
        mvc.perform(post("/api/contas-pagar")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"dataVencimento\": \"2030-12-31\" }"))
                .andExpect(status().isBadRequest());
    }
}
