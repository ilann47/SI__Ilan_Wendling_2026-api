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
 * Teste de integracao da operacao de patio (entrada/saida) — todos os fluxos:
 * deteccao de mensalista, regra de 1 aberta por veiculo, listagem, patio atual,
 * fechamento e tentativa de saida de movimentacao ja fechada.
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

    private Number entrar(Long veiculoId) throws Exception {
        String json = "{ \"veiculoId\": " + veiculoId + " }";
        String body = mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void rejeitaEntradaDeVeiculoInativo() throws Exception {
        Veiculo inativo = new Veiculo();
        inativo.setPlaca("INA0000");
        inativo.setAtivo(false);
        Long inativoId = veiculoRepository.save(inativo).getId();
        mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"veiculoId\": " + inativoId + " }"))
                .andExpect(status().isConflict());
    }

    @Test
    void fluxoEntradaSaidaAvulso() throws Exception {
        Number id = entrar(veiculoAvulsoId);
        mvc.perform(get("/api/movimentacoes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("AVULSO"))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.tarifaId").isNotEmpty())
                .andExpect(jsonPath("$.mensalistaId").isEmpty());

        mvc.perform(post("/api/movimentacoes/" + id + "/saida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.dataSaida").isNotEmpty())
                .andExpect(jsonPath("$.valorCobrado").isNotEmpty());
    }

    @Test
    void listaMovimentacoes() throws Exception {
        entrar(veiculoAvulsoId);
        mvc.perform(get("/api/movimentacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void patioMostraApenasAbertas() throws Exception {
        Number aberta = entrar(veiculoAvulsoId);
        Number fechada = entrar(veiculoMensalistaId);
        mvc.perform(post("/api/movimentacoes/" + fechada + "/saida"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/movimentacoes/patio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void naoPermiteEntrada2xNoMesmoVeiculo() throws Exception {
        entrar(veiculoAvulsoId);
        mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"veiculoId\": " + veiculoAvulsoId + " }"))
                .andExpect(status().isConflict());
    }

    @Test
    void entradaDetectaMensalistaAtivo() throws Exception {
        mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"veiculoId\": " + veiculoMensalistaId + " }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("MENSALISTA"))
                .andExpect(jsonPath("$.mensalistaId").isNotEmpty())
                .andExpect(jsonPath("$.tarifaId").isEmpty());
    }

    @Test
    void saidaDeMensalistaNaoCobra() throws Exception {
        Number id = entrar(veiculoMensalistaId);
        mvc.perform(post("/api/movimentacoes/" + id + "/saida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.valorCobrado").value(0));
    }

    @Test
    void naoPermiteSaidaDeMovimentacaoJaFechada() throws Exception {
        Number id = entrar(veiculoAvulsoId);
        mvc.perform(post("/api/movimentacoes/" + id + "/saida"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/movimentacoes/" + id + "/saida"))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoNaoEncontrada() throws Exception {
        mvc.perform(get("/api/movimentacoes/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404QuandoVeiculoNaoExiste() throws Exception {
        mvc.perform(post("/api/movimentacoes/entrada")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"veiculoId\": 999999 }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelaMovimentacaoAberta() throws Exception {
        Number id = entrar(veiculoAvulsoId);
        mvc.perform(post("/api/movimentacoes/" + id + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    @Test
    void naoCancelaMovimentacaoFechada() throws Exception {
        Number id = entrar(veiculoAvulsoId);
        mvc.perform(post("/api/movimentacoes/" + id + "/saida"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/movimentacoes/" + id + "/cancelamento"))
                .andExpect(status().isConflict());
    }
}
