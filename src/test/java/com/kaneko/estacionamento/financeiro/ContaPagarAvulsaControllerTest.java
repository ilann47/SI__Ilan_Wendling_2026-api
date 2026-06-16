package com.kaneko.estacionamento.financeiro;

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
 * Teste de integracao da despesa avulsa a pagar — todos os fluxos, incluindo a
 * baixa parcial/total e o cancelamento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContaPagarAvulsaControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String descricao, String valorOriginal) throws Exception {
        String json = "{ \"descricao\": \"" + descricao + "\", \"valorOriginal\": " + valorOriginal
                + ", \"dataVencimento\": \"2030-12-31\" }";
        String body = mvc.perform(post("/api/contas-pagar-avulsas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaComValorTotalCalculadoEbuscaPorId() throws Exception {
        Number id = criar("Aluguel", "1500.00");
        mvc.perform(get("/api/contas-pagar-avulsas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Aluguel"))
                .andExpect(jsonPath("$.valorTotal").value(1500.00))
                .andExpect(jsonPath("$.situacao").value("PENDENTE"));
    }

    @Test
    void listaContas() throws Exception {
        criar("Luz", "300.00");
        criar("Agua", "150.00");
        mvc.perform(get("/api/contas-pagar-avulsas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void atualizaConta() throws Exception {
        Number id = criar("Internet", "100.00");
        String json = "{ \"descricao\": \"Internet Fibra\", \"valorOriginal\": 120.00, \"dataVencimento\": \"2030-12-31\" }";
        mvc.perform(put("/api/contas-pagar-avulsas/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Internet Fibra"))
                .andExpect(jsonPath("$.valorTotal").value(120.00));
    }

    @Test
    void baixaParcialMudaSituacaoParaParcial() throws Exception {
        Number id = criar("Salario", "2000.00");
        mvc.perform(post("/api/contas-pagar-avulsas/" + id + "/baixa")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"valor\": 500.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PARCIAL"))
                .andExpect(jsonPath("$.valorPago").value(500.00));
    }

    @Test
    void baixaTotalMudaSituacaoParaPaga() throws Exception {
        Number id = criar("Aluguel", "1000.00");
        mvc.perform(post("/api/contas-pagar-avulsas/" + id + "/baixa")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"valor\": 1000.00 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PAGA"))
                .andExpect(jsonPath("$.dataPagamento").isNotEmpty());
    }

    @Test
    void rejeitaBaixaQueExcedeOSaldoCom409() throws Exception {
        Number id = criar("Conta", "100.00");
        mvc.perform(post("/api/contas-pagar-avulsas/" + id + "/baixa")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"valor\": 200.00 }"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelaConta() throws Exception {
        Number id = criar("Erro", "50.00");
        mvc.perform(post("/api/contas-pagar-avulsas/" + id + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"));
    }

    @Test
    void excluiContaEdepoisRetorna404() throws Exception {
        Number id = criar("Temporaria", "10.00");
        mvc.perform(delete("/api/contas-pagar-avulsas/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/contas-pagar-avulsas/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/contas-pagar-avulsas/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoDescricaoOuValorAusente() throws Exception {
        mvc.perform(post("/api/contas-pagar-avulsas")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"dataVencimento\": \"2030-12-31\" }"))
                .andExpect(status().isBadRequest());
    }
}
