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
 * Teste de integracao do CRUD de Cidade, exercitando a referencia (FK) ao
 * Estado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CidadeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaisRepository paisRepository;

    @Autowired
    private EstadoRepository estadoRepository;

    private Long estadoId;

    @BeforeEach
    void setup() {
        Pais pais = new Pais();
        pais.setNome("Brasil");
        pais = paisRepository.save(pais);

        Estado estado = new Estado();
        estado.setNome("Parana");
        estado.setUf("PR");
        estado.setPais(pais);
        estadoId = estadoRepository.save(estado).getId();
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Maringa", "ddd": "44", "codigoIbge": "4115200", "estadoId": %d }
                """.formatted(estadoId);
        String body = mvc.perform(post("/api/cidades")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Maringa"))
                .andExpect(jsonPath("$.estadoId").value(estadoId))
                .andExpect(jsonPath("$.estadoNome").value("Parana"))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/cidades/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ddd").value("44"));
    }

    @Test
    void rejeitaNomeDuplicadoNoMesmoEstadoCom409() throws Exception {
        String json = """
                { "nome": "Londrina", "estadoId": %d }
                """.formatted(estadoId);
        mvc.perform(post("/api/cidades")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/cidades")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoEstadoNaoExiste() throws Exception {
        String json = """
                { "nome": "Curitiba", "estadoId": 999999 }
                """;
        mvc.perform(post("/api/cidades")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoCamposObrigatoriosAusentes() throws Exception {
        mvc.perform(post("/api/cidades")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"\" }"))
                .andExpect(status().isBadRequest());
    }
}
