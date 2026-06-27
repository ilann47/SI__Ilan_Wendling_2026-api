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
 * Teste de integracao do CRUD de Cidade — todos os fluxos, exercitando a FK
 * para Estado.
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

    private Number criar(String nome) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"estadoId\": " + estadoId + " }";
        String body = mvc.perform(post("/api/cidades")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"nome\": \"Maringa\", \"ddd\": \"44\", \"codigoIbge\": \"4115200\", \"estadoId\": " + estadoId + " }";
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
    void listaCidades() throws Exception {
        criar("Maringa");
        criar("Londrina");
        mvc.perform(get("/api/cidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Maringa");
        criar("Londrina");

        mvc.perform(get("/api/cidades").param("nome", "marin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Maringa"));
    }

    @Test
    void atualizaCidade() throws Exception {
        Number id = criar("Marina");
        String json = "{ \"nome\": \"Maringa\", \"ddd\": \"44\", \"estadoId\": " + estadoId + " }";
        mvc.perform(put("/api/cidades/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maringa"))
                .andExpect(jsonPath("$.ddd").value("44"));
    }

    @Test
    void atualizaCidadeInexistenteRetorna404() throws Exception {
        String json = "{ \"nome\": \"X\", \"estadoId\": " + estadoId + " }";
        mvc.perform(put("/api/cidades/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiCidadeEdepoisRetorna404() throws Exception {
        Number id = criar("Temporaria");
        mvc.perform(delete("/api/cidades/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/cidades/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaNomeDuplicadoNoMesmoEstadoCom409() throws Exception {
        criar("Londrina");
        String json = "{ \"nome\": \"Londrina\", \"estadoId\": " + estadoId + " }";
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
