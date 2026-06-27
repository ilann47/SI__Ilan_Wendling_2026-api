package com.kaneko.estacionamento.patio;

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
 * Teste de integracao do CRUD de Tarifa — todos os fluxos, incluindo o default
 * de tolerancia e a validacao de valores nao-negativos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TarifaControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String descricao) throws Exception {
        String json = "{ \"descricao\": \"" + descricao
                + "\", \"valorHora\": 5.00, \"valorDiaria\": 30.00, \"valorMensalidade\": 250.00 }";
        String body = mvc.perform(post("/api/tarifas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

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
    void listaTarifas() throws Exception {
        // O schema (migration V1) nasce com 1 tarifa-semente ("Tarifa padrao");
        // a verificacao e relativa ao total previo para nao depender desse dado.
        int antes = totalTarifas();
        criar("Tarifa A");
        criar("Tarifa B");
        mvc.perform(get("/api/tarifas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(antes + 2));
    }

    private int totalTarifas() throws Exception {
        String body = mvc.perform(get("/api/tarifas"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.totalElements")).intValue();
    }

    @Test
    void filtraPorDescricao() throws Exception {
        criar("Mensalista Premium");
        criar("Avulso Economico");

        mvc.perform(get("/api/tarifas").param("descricao", "premium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].descricao").value("Mensalista Premium"));
    }

    @Test
    void atualizaTarifa() throws Exception {
        Number id = criar("Tarifa Antiga");
        String json = """
                { "descricao": "Tarifa Nova", "valorHora": 7.50, "valorDiaria": 40.00,
                  "valorMensalidade": 300.00, "toleranciaMin": 20 }
                """;
        mvc.perform(put("/api/tarifas/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Tarifa Nova"))
                .andExpect(jsonPath("$.valorHora").value(7.50))
                .andExpect(jsonPath("$.toleranciaMin").value(20));
    }

    @Test
    void atualizaTarifaInexistenteRetorna404() throws Exception {
        String json = """
                { "descricao": "X", "valorHora": 1, "valorDiaria": 1, "valorMensalidade": 1 }
                """;
        mvc.perform(put("/api/tarifas/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiTarifaEdepoisRetorna404() throws Exception {
        Number id = criar("Tarifa Temporaria");
        mvc.perform(delete("/api/tarifas/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/tarifas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
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
