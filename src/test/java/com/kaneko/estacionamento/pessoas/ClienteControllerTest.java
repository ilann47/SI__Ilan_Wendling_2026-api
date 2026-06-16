package com.kaneko.estacionamento.pessoas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.geografia.Estado;
import com.kaneko.estacionamento.geografia.EstadoRepository;
import com.kaneko.estacionamento.geografia.Pais;
import com.kaneko.estacionamento.geografia.PaisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do CRUD de Cliente, incluindo a unicidade de documento e
 * a referencia opcional a Cidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClienteControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaisRepository paisRepository;

    @Autowired
    private EstadoRepository estadoRepository;

    @Autowired
    private CidadeRepository cidadeRepository;

    private Long cidadeId;

    @BeforeEach
    void setup() {
        Pais pais = new Pais();
        pais.setNome("Brasil");
        pais = paisRepository.save(pais);

        Estado estado = new Estado();
        estado.setNome("Parana");
        estado.setUf("PR");
        estado.setPais(pais);
        estado = estadoRepository.save(estado);

        Cidade cidade = new Cidade();
        cidade.setNome("Maringa");
        cidade.setEstado(estado);
        cidadeId = cidadeRepository.save(cidade).getId();
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = """
                { "nome": "Joao da Silva", "documento": "12345678901", "tipo": "FISICA",
                  "sexo": "MASCULINO", "cidadeId": %d, "limiteCredito": 500.00 }
                """.formatted(cidadeId);
        String body = mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Joao da Silva"))
                .andExpect(jsonPath("$.tipo").value("FISICA"))
                .andExpect(jsonPath("$.cidadeId").value(cidadeId))
                .andExpect(jsonPath("$.cidadeNome").value("Maringa"))
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/clientes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("12345678901"));
    }

    @Test
    void rejeitaDocumentoDuplicadoCom409() throws Exception {
        String json = """
                { "nome": "Cliente A", "documento": "99999999999" }
                """;
        mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoCidadeNaoExiste() throws Exception {
        String json = """
                { "nome": "Cliente B", "documento": "88888888888", "cidadeId": 999999 }
                """;
        mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoDocumentoAusente() throws Exception {
        mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Sem Doc\" }"))
                .andExpect(status().isBadRequest());
    }
}
