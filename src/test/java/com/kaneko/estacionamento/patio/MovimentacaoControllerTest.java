package com.kaneko.estacionamento.patio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao da operacao de patio (entrada/saida), cobrindo a
 * deteccao de mensalista, a regra de 1 aberta por veiculo e o fechamento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MovimentacaoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TarifaRepository tarifaRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private MensalistaRepository mensalistaRepository;

    private Long veiculoAvulsoId;
    private Long veiculoMensalistaId;

    @BeforeEach
    void setup() {
        Tarifa tarifa = new Tarifa();
        tarifa.setDescricao("Vigente");
        tarifa.setValorHora(new BigDecimal("5.00"));
        tarifa.setValorDiaria(new BigDecimal("30.00"));
        tarifa.setValorMensalidade(new BigDecimal("250.00"));
        tarifa.setToleranciaMin(15);
        tarifaRepository.save(tarifa);

        Veiculo avulso = new Veiculo();
        avulso.setPlaca("AVU1234");
        veiculoAvulsoId = veiculoRepository.save(avulso).getId();

        Cliente cliente = new Cliente();
        cliente.setNome("Mensalista Dono");
        cliente.setDocumento("99988877766");
        cliente = clienteRepository.save(cliente);

        Veiculo veiculoMens = new Veiculo();
        veiculoMens.setPlaca("MEN9999");
        veiculoMens.setCliente(cliente);
        veiculoMens = veiculoRepository.save(veiculoMens);
        veiculoMensalistaId = veiculoMens.getId();

        Mensalista mensalista = new Mensalista();
        mensalista.setCliente(cliente);
        mensalista.setVeiculo(veiculoMens);
        mensalista.setValorMensal(new BigDecimal("250.00"));
        mensalista.setDataInicio(LocalDate.now());
        mensalistaRepository.save(mensalista);
    }

    @Test
    void fluxoEntradaSaidaAvulso() throws Exception {
        String entrada = """
                { "veiculoId": %d }
                """.formatted(veiculoAvulsoId);
        String body = mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AVULSO"))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.tarifaId").isNotEmpty())
                .andExpect(jsonPath("$.mensalistaId").isEmpty())
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/movimentacoes/" + id + "/saida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.dataSaida").isNotEmpty())
                .andExpect(jsonPath("$.valorCobrado").isNotEmpty());
    }

    @Test
    void naoPermiteEntrada2xNoMesmoVeiculo() throws Exception {
        String entrada = """
                { "veiculoId": %d }
                """.formatted(veiculoAvulsoId);
        mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isConflict());
    }

    @Test
    void entradaDetectaMensalistaAtivo() throws Exception {
        String entrada = """
                { "veiculoId": %d }
                """.formatted(veiculoMensalistaId);
        mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("MENSALISTA"))
                .andExpect(jsonPath("$.mensalistaId").isNotEmpty())
                .andExpect(jsonPath("$.tarifaId").isEmpty());
    }

    @Test
    void saidaDeMensalistaNaoCobra() throws Exception {
        String entrada = """
                { "veiculoId": %d }
                """.formatted(veiculoMensalistaId);
        String body = mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");

        mvc.perform(post("/api/movimentacoes/" + id + "/saida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.valorCobrado").value(0));
    }

    @Test
    void retorna404QuandoVeiculoNaoExiste() throws Exception {
        mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"veiculoId\": 999999 }"))
                .andExpect(status().isNotFound());
    }
}
