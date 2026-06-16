package com.kaneko.estacionamento.patio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Teste de integracao do contrato de Mensalista, exercitando as FKs
 * (cliente/veiculo) e a regra de no maximo um contrato ATIVO por veiculo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MensalistaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    private Long clienteId;
    private Long veiculoId;

    @BeforeEach
    void setup() {
        Cliente cliente = new Cliente();
        cliente.setNome("Mensalista X");
        cliente.setDocumento("55566677788");
        clienteId = clienteRepository.save(cliente).getId();

        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca("MEN1234");
        veiculoId = veiculoRepository.save(veiculo).getId();
    }

    @Test
    void criaContratoComStatusAtivoPadrao() throws Exception {
        String json = """
                { "clienteId": %d, "veiculoId": %d, "valorMensal": 250.00 }
                """.formatted(clienteId, veiculoId);
        String body = mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.veiculoPlaca").value("MEN1234"))
                .andExpect(jsonPath("$.dataInicio").exists())
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/mensalistas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNome").value("Mensalista X"));
    }

    @Test
    void rejeitaSegundoContratoAtivoNoMesmoVeiculoCom409() throws Exception {
        String json = """
                { "clienteId": %d, "veiculoId": %d, "valorMensal": 250 }
                """.formatted(clienteId, veiculoId);
        mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoVeiculoNaoExiste() throws Exception {
        String json = """
                { "clienteId": %d, "veiculoId": 999999, "valorMensal": 250 }
                """.formatted(clienteId);
        mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaPeriodoInvalidoCom409() throws Exception {
        String json = """
                { "clienteId": %d, "veiculoId": %d, "valorMensal": 250,
                  "dataInicio": "2026-01-10", "dataFim": "2026-01-05" }
                """.formatted(clienteId, veiculoId);
        mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna400QuandoCamposObrigatoriosAusentes() throws Exception {
        mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content("{ }"))
                .andExpect(status().isBadRequest());
    }
}
