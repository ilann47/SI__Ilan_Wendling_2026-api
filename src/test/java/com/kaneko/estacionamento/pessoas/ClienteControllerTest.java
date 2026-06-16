package com.kaneko.estacionamento.pessoas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Teste de integracao do CRUD de Cliente — todos os fluxos, com a unicidade de
 * documento e a referencia opcional a Cidade.
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

    private Number criar(String nome, String documento) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"documento\": \"" + documento + "\" }";
        String body = mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"nome\": \"Joao da Silva\", \"documento\": \"12345678901\", \"tipo\": \"FISICA\","
                + " \"sexo\": \"MASCULINO\", \"cidadeId\": " + cidadeId + ", \"limiteCredito\": 500.00 }";
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
    void listaClientes() throws Exception {
        criar("Cliente Um", "11111111111");
        criar("Cliente Dois", "22222222222");
        mvc.perform(get("/api/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void atualizaCliente() throws Exception {
        Number id = criar("Maria", "33333333333");
        String json = "{ \"nome\": \"Maria Souza\", \"documento\": \"33333333333\","
                + " \"apelido\": \"Mari\", \"limiteCredito\": 1500.00 }";
        mvc.perform(put("/api/clientes/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Souza"))
                .andExpect(jsonPath("$.apelido").value("Mari"))
                .andExpect(jsonPath("$.limiteCredito").value(1500.00));
    }

    @Test
    void atualizaClienteInexistenteRetorna404() throws Exception {
        String json = "{ \"nome\": \"X\", \"documento\": \"00000000000\" }";
        mvc.perform(put("/api/clientes/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiClienteEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario", "44444444444");
        mvc.perform(delete("/api/clientes/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/clientes/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaDocumentoDuplicadoCom409() throws Exception {
        criar("Cliente A", "99999999999");
        mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Cliente B\", \"documento\": \"99999999999\" }"))
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
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/clientes/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoDocumentoAusente() throws Exception {
        mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": \"Sem Doc\" }"))
                .andExpect(status().isBadRequest());
    }
}
