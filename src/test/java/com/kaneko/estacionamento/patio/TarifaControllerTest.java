package com.kaneko.estacionamento.patio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Teste de integracao do CRUD de Tarifa, incluindo o default de tolerancia e
 * a validacao de valores nao-negativos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TarifaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void criaComToleranciaPadraoEbuscaPorId() throws Exception {
        String json = """
                { "descricao": "Tarifa Padrao", "valorHora": 5.00, "valorDiaria": 30.00, "valorMensalidade": 250.00 }
                """;
        String body = mvc.perform(post("/api/tarifas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricao").value("Tarifa Padrao"))
                .andExpect(jsonPath("$.toleranciaMin").value(15))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/tarifas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorDiaria").value(30.00));
    }

    @Test
    void retorna400QuandoValorNegativo() throws Exception {
        String json = """
                { "descricao": "Invalida", "valorHora": -1, "valorDiaria": 30, "valorMensalidade": 250 }
                """;
        mvc.perform(post("/api/tarifas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna400QuandoDescricaoVazia() throws Exception {
        String json = """
                { "descricao": "", "valorHora": 5, "valorDiaria": 30, "valorMensalidade": 250 }
                """;
        mvc.perform(post("/api/tarifas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna404QuandoNaoEncontrada() throws Exception {
        mvc.perform(get("/api/tarifas/999999"))
                .andExpect(status().isNotFound());
    }
}
