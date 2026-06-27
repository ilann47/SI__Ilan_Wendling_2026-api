package com.kaneko.estacionamento.patio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do CRUD de Veiculo — todos os fluxos (avulso e vinculado
 * a cliente).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VeiculoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClienteRepository clienteRepository;

    private Long clienteId;

    @BeforeEach
    void setup() {
        Cliente cliente = new Cliente();
        cliente.setNome("Dono do Carro");
        cliente.setDocumento("11122233344");
        clienteId = clienteRepository.save(cliente).getId();
    }

    private Number criar(String placa) throws Exception {
        String json = "{ \"placa\": \"" + placa + "\" }";
        String body = mvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaAvulsoNormalizandoPlaca() throws Exception {
        String json = """
                { "placa": "abc1d23", "modelo": "Gol", "tipo": "CARRO" }
                """;
        String body = mvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ABC1D23"))
                .andExpect(jsonPath("$.clienteId").isEmpty())
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/veiculos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("Gol"));
    }

    @Test
    void criaVinculadoAoCliente() throws Exception {
        String json = "{ \"placa\": \"XYZ4E56\", \"tipo\": \"MOTO\", \"clienteId\": " + clienteId + " }";
        mvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andExpect(jsonPath("$.clienteNome").value("Dono do Carro"));
    }

    @Test
    void listaVeiculos() throws Exception {
        criar("AAA1111");
        criar("BBB2222");
        mvc.perform(get("/api/veiculos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorPlaca() throws Exception {
        criar("AAA1111");
        criar("BBB2222");

        mvc.perform(get("/api/veiculos").param("placa", "aaa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].placa").value("AAA1111"));
    }

    @Test
    void atualizaVeiculo() throws Exception {
        Number id = criar("UPD1234");
        String json = """
                { "placa": "upd1234", "modelo": "Onix", "cor": "Prata", "tipo": "CARRO" }
                """;
        mvc.perform(put("/api/veiculos/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("Onix"))
                .andExpect(jsonPath("$.cor").value("Prata"));
    }

    @Test
    void atualizaVeiculoInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/veiculos/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"placa\": \"NEW0001\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiVeiculoEdepoisRetorna404() throws Exception {
        Number id = criar("DEL1234");
        mvc.perform(delete("/api/veiculos/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/veiculos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaPlacaDuplicadaCom409() throws Exception {
        criar("DUP1234");
        mvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"placa\": \"DUP1234\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoClienteNaoExiste() throws Exception {
        String json = """
                { "placa": "NEW0009", "clienteId": 999999 }
                """;
        mvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoPlacaAusente() throws Exception {
        mvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"modelo\": \"Sem Placa\" }"))
                .andExpect(status().isBadRequest());
    }
}
