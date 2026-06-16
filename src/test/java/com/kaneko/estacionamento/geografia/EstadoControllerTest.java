package com.kaneko.estacionamento.geografia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Teste de integracao do CRUD de Estado, exercitando a referencia (FK) ao Pais.
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

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Parana", "uf": "pr", "paisId": %d }
                """.formatted(paisId);
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
    void rejeitaUfDuplicadaNoMesmoPaisCom409() throws Exception {
        String json = """
                { "nome": "Sao Paulo", "uf": "SP", "paisId": %d }
                """.formatted(paisId);
        mvc.perform(post("/api/estados")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
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
