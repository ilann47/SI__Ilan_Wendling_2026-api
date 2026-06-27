package com.kaneko.estacionamento.geografia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do CRUD de Estado — todos os fluxos, exercitando a FK
 * para Pais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstadoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaisRepository paisRepository;

    private Long paisId;

    @BeforeEach
    void setup() {
        Pais pais = new Pais();
        pais.setNome("Brasil");
        paisId = paisRepository.save(pais).getId();
    }

    private Number criar(String nome, String uf) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"uf\": \"" + uf + "\", \"paisId\": " + paisId + " }";
        String body = mvc.perform(post("/api/estados")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"nome\": \"Parana\", \"uf\": \"pr\", \"paisId\": " + paisId + " }";
        String body = mvc.perform(post("/api/estados")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uf").value("PR"))
                .andExpect(jsonPath("$.paisId").value(paisId))
                .andExpect(jsonPath("$.paisNome").value("Brasil"))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/estados/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Parana"));
    }

    @Test
    void listaEstados() throws Exception {
        criar("Parana", "PR");
        criar("Sao Paulo", "SP");
        mvc.perform(get("/api/estados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorUf() throws Exception {
        criar("Parana", "PR");
        criar("Sao Paulo", "SP");

        mvc.perform(get("/api/estados").param("uf", "pr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Parana"));
    }

    @Test
    void atualizaEstado() throws Exception {
        Number id = criar("Sao Pauo", "SP");
        String json = "{ \"nome\": \"Sao Paulo\", \"uf\": \"sp\", \"paisId\": " + paisId + " }";
        mvc.perform(put("/api/estados/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sao Paulo"))
                .andExpect(jsonPath("$.uf").value("SP"));
    }

    @Test
    void atualizaEstadoInexistenteRetorna404() throws Exception {
        String json = "{ \"nome\": \"X\", \"uf\": \"XX\", \"paisId\": " + paisId + " }";
        mvc.perform(put("/api/estados/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiEstadoEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario", "TP");
        mvc.perform(delete("/api/estados/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/estados/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaUfDuplicadaNoMesmoPaisCom409() throws Exception {
        criar("Sao Paulo", "SP");
        String json = "{ \"nome\": \"Outro\", \"uf\": \"SP\", \"paisId\": " + paisId + " }";
        mvc.perform(post("/api/estados")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoPaisNaoExiste() throws Exception {
        String json = """
                { "nome": "Distrito", "uf": "DF", "paisId": 999999 }
                """;
        mvc.perform(post("/api/estados")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoCamposObrigatoriosAusentes() throws Exception {
        mvc.perform(post("/api/estados")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }
}
