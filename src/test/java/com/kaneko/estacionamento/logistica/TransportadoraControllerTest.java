package com.kaneko.estacionamento.logistica;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste de integracao do cadastro de Transportadora — todos os fluxos, com a
 * unicidade do documento.
 */
@TenantApiIntegrationTest
class TransportadoraControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantTestSupport tenant;

    private String token;

    @BeforeEach
    void setup() throws Exception {
        token = tenant.criarTenantAdmin().tokenContextual();
    }

    private Number criar(String nome, String documento) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"documento\": \"" + documento + "\" }";
        String body = mvc.perform(post("/api/transportadoras")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"nome\": \"Rapido Oeste\", \"documento\": \"44555666000177\", \"tipo\": \"JURIDICA\","
                + " \"email\": \"contato@rapido.com\" }";
        String body = mvc.perform(post("/api/transportadoras")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Rapido Oeste"))
                .andExpect(jsonPath("$.documento").value("44555666000177"))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/transportadoras/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("contato@rapido.com"));
    }

    @Test
    void listaTransportadoras() throws Exception {
        criar("Rapido Oeste", "44555666000177");
        criar("TransCataratas", "55666777000188");
        mvc.perform(get("/api/transportadoras").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Rapido Oeste", "44555666000177");
        criar("TransCataratas", "55666777000188");
        mvc.perform(get("/api/transportadoras").header("Authorization", token).param("nome", "cataratas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("TransCataratas"));
    }

    @Test
    void atualizaTransportadora() throws Exception {
        Number id = criar("Nome Antigo", "44555666000177");
        String json = "{ \"nome\": \"Nome Novo\", \"documento\": \"44555666000177\" }";
        mvc.perform(put("/api/transportadoras/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Novo"));
    }

    @Test
    void atualizaInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/transportadoras/999999")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"X\", \"documento\": \"11111111000199\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiTransportadoraEdepoisRetorna404() throws Exception {
        Number id = criar("Temporaria", "44555666000177");
        mvc.perform(delete("/api/transportadoras/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/transportadoras/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaDocumentoDuplicadoCom409() throws Exception {
        criar("Primeira", "44555666000177");
        mvc.perform(post("/api/transportadoras")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Segunda\", \"documento\": \"44555666000177\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrada() throws Exception {
        mvc.perform(get("/api/transportadoras/999999").header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoNomeAusente() throws Exception {
        mvc.perform(post("/api/transportadoras")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"documento\": \"44555666000177\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna400QuandoDocumentoAusente() throws Exception {
        mvc.perform(post("/api/transportadoras")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Sem Documento\" }"))
                .andExpect(status().isBadRequest());
    }
}
