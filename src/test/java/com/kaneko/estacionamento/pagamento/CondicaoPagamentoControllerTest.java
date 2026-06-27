package com.kaneko.estacionamento.pagamento;

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
 * Teste de integracao da condicao de pagamento — todos os fluxos, incluindo o
 * detalhamento de parcelas e a validacao de soma de percentuais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CondicaoPagamentoControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"numeroParcelas\": 1 }";
        String body = mvc.perform(post("/api/condicoes-pagamento")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        Number id = criar("A Vista");
        mvc.perform(get("/api/condicoes-pagamento/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("A Vista"))
                .andExpect(jsonPath("$.numeroParcelas").value(1));
    }

    @Test
    void criaComParcelasSomando100() throws Exception {
        String json = """
                { "nome": "30/60", "numeroParcelas": 2, "diasPrimeiraParcela": 30, "diasEntreParcelas": 30,
                  "parcelas": [
                    { "numero": 1, "dias": 30, "percentual": 50.00 },
                    { "numero": 2, "dias": 60, "percentual": 50.00 }
                  ] }
                """;
        mvc.perform(post("/api/condicoes-pagamento")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelas.length()").value(2))
                .andExpect(jsonPath("$.parcelas[0].percentual").value(50.00));
    }

    @Test
    void rejeitaParcelasQueNaoSomam100Com409() throws Exception {
        String json = """
                { "nome": "Errada", "numeroParcelas": 2,
                  "parcelas": [
                    { "numero": 1, "dias": 30, "percentual": 40.00 },
                    { "numero": 2, "dias": 60, "percentual": 40.00 }
                  ] }
                """;
        mvc.perform(post("/api/condicoes-pagamento")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void listaCondicoes() throws Exception {
        criar("A Vista");
        criar("30 dias");
        mvc.perform(get("/api/condicoes-pagamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("A Vista");
        criar("30 dias");
        mvc.perform(get("/api/condicoes-pagamento").param("nome", "vista"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("A Vista"));
    }

    @Test
    void atualizaCondicao() throws Exception {
        Number id = criar("Provisorio");
        String json = "{ \"nome\": \"Definitivo\", \"numeroParcelas\": 3, \"percentualJuros\": 2.00 }";
        mvc.perform(put("/api/condicoes-pagamento/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Definitivo"))
                .andExpect(jsonPath("$.numeroParcelas").value(3));
    }

    @Test
    void atualizaCondicaoInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/condicoes-pagamento/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"X\", \"numeroParcelas\": 1 }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiCondicaoEdepoisRetorna404() throws Exception {
        Number id = criar("Temporaria");
        mvc.perform(delete("/api/condicoes-pagamento/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/condicoes-pagamento/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoCom409() throws Exception {
        criar("Unica");
        mvc.perform(post("/api/condicoes-pagamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Unica\", \"numeroParcelas\": 1 }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrada() throws Exception {
        mvc.perform(get("/api/condicoes-pagamento/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeAusente() throws Exception {
        mvc.perform(post("/api/condicoes-pagamento")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"numeroParcelas\": 1 }"))
                .andExpect(status().isBadRequest());
    }
}
