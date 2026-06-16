package com.kaneko.estacionamento.fornecedor;

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
 * Teste de integracao do cadastro de Fornecedor — todos os fluxos, incluindo as
 * colecoes de contato e a regra de "um principal".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FornecedorControllerTest {

    @Autowired
    private MockMvc mvc;

    private Number criar(String nome, String documento) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"documento\": \"" + documento + "\" }";
        String body = mvc.perform(post("/api/fornecedores")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaComContatosEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Distribuidora X", "documento": "11222333000144", "tipo": "JURIDICA",
                  "emails": [ { "email": "compras@x.com", "tipo": "COMPRAS", "principal": true } ],
                  "telefones": [ { "telefone": "4433221100", "tipo": "COMERCIAL", "principal": true } ] }
                """;
        String body = mvc.perform(post("/api/fornecedores")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Distribuidora X"))
                .andExpect(jsonPath("$.emails.length()").value(1))
                .andExpect(jsonPath("$.emails[0].principal").value(true))
                .andExpect(jsonPath("$.telefones[0].telefone").value("4433221100"))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/fornecedores/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("11222333000144"));
    }

    @Test
    void rejeitaDoisEmailsPrincipaisCom409() throws Exception {
        String json = """
                { "nome": "Erro", "documento": "99888777000166",
                  "emails": [
                    { "email": "a@x.com", "principal": true },
                    { "email": "b@x.com", "principal": true }
                  ] }
                """;
        mvc.perform(post("/api/fornecedores")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void listaFornecedores() throws Exception {
        criar("Fornecedor Um", "11111111111111");
        criar("Fornecedor Dois", "22222222222222");
        mvc.perform(get("/api/fornecedores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void atualizaFornecedor() throws Exception {
        Number id = criar("Antigo", "33333333333333");
        String json = "{ \"nome\": \"Novo Nome\", \"documento\": \"33333333333333\", \"limiteCredito\": 5000.00 }";
        mvc.perform(put("/api/fornecedores/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"))
                .andExpect(jsonPath("$.limiteCredito").value(5000.00));
    }

    @Test
    void atualizaFornecedorInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/fornecedores/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"X\", \"documento\": \"00000000000000\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiFornecedorEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario", "44444444444444");
        mvc.perform(delete("/api/fornecedores/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/fornecedores/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaDocumentoDuplicadoCom409() throws Exception {
        criar("Fornecedor A", "99999999999999");
        mvc.perform(post("/api/fornecedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Fornecedor B\", \"documento\": \"99999999999999\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoCondicaoPagamentoNaoExiste() throws Exception {
        String json = "{ \"nome\": \"Sem Condicao\", \"documento\": \"55555555555555\", \"condicaoPagamentoId\": 999999 }";
        mvc.perform(post("/api/fornecedores")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/fornecedores/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoDocumentoAusente() throws Exception {
        mvc.perform(post("/api/fornecedores")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Sem Doc\" }"))
                .andExpect(status().isBadRequest());
    }
}
