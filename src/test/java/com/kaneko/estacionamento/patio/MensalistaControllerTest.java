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
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao do contrato de Mensalista — todos os fluxos, exercitando
 * as FKs e a regra de no maximo um contrato ATIVO por veiculo.
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

    private Number criar(String status) throws Exception {
        String campoStatus = status != null ? ", \"status\": \"" + status + "\"" : "";
        String json = "{ \"clienteId\": " + clienteId + ", \"veiculoId\": " + veiculoId
                + ", \"valorMensal\": 250.00" + campoStatus + " }";
        String body = mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaContratoComStatusAtivoPadrao() throws Exception {
        String json = "{ \"clienteId\": " + clienteId + ", \"veiculoId\": " + veiculoId
                + ", \"valorMensal\": 250.00 }";
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
    void listaMensalistas() throws Exception {
        criar(null);          // ATIVO
        criar("SUSPENSO");    // segundo contrato (nao conflita com o ATIVO)
        mvc.perform(get("/api/mensalistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void atualizaMensalista() throws Exception {
        Number id = criar(null);
        String json = "{ \"clienteId\": " + clienteId + ", \"veiculoId\": " + veiculoId
                + ", \"valorMensal\": 300.00, \"status\": \"ATIVO\" }";
        mvc.perform(put("/api/mensalistas/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorMensal").value(300.00));
    }

    @Test
    void atualizaMensalistaInexistenteRetorna404() throws Exception {
        String json = "{ \"clienteId\": " + clienteId + ", \"veiculoId\": " + veiculoId
                + ", \"valorMensal\": 250 }";
        mvc.perform(put("/api/mensalistas/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiMensalistaEdepoisRetorna404() throws Exception {
        Number id = criar(null);
        mvc.perform(delete("/api/mensalistas/" + id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/mensalistas/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaSegundoContratoAtivoNoMesmoVeiculoCom409() throws Exception {
        criar(null);
        String json = "{ \"clienteId\": " + clienteId + ", \"veiculoId\": " + veiculoId
                + ", \"valorMensal\": 250 }";
        mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoVeiculoNaoExiste() throws Exception {
        String json = "{ \"clienteId\": " + clienteId + ", \"veiculoId\": 999999, \"valorMensal\": 250 }";
        mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaPeriodoInvalidoCom409() throws Exception {
        String json = "{ \"clienteId\": " + clienteId + ", \"veiculoId\": " + veiculoId
                + ", \"valorMensal\": 250, \"dataInicio\": \"2026-01-10\", \"dataFim\": \"2026-01-05\" }";
        mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void geraMensalidadeParaContratoAtivo() throws Exception {
        Number id = criar(null);
        mvc.perform(post("/api/mensalistas/" + id + "/mensalidade").param("vencimento", "2030-12-10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorTotal").value(250.00))
                .andExpect(jsonPath("$.situacao").value("PENDENTE"))
                .andExpect(jsonPath("$.mensalistaId").value(id))
                .andExpect(jsonPath("$.clienteNome").value("Mensalista X"));

        mvc.perform(get("/api/contas-receber"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void naoGeraMensalidadeParaContratoNaoAtivo() throws Exception {
        Number id = criar("SUSPENSO");
        mvc.perform(post("/api/mensalistas/" + id + "/mensalidade"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/mensalistas/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoCamposObrigatoriosAusentes() throws Exception {
        mvc.perform(post("/api/mensalistas")
                        .contentType(MediaType.APPLICATION_JSON).content("{ }"))
                .andExpect(status().isBadRequest());
    }
}
