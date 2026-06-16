package com.kaneko.estacionamento.relatorio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaneko.estacionamento.conveniencia.Marca;
import com.kaneko.estacionamento.conveniencia.MarcaRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.conveniencia.UnidadeMedida;
import com.kaneko.estacionamento.conveniencia.UnidadeMedidaRepository;
import com.kaneko.estacionamento.financeiro.ContaPagar;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaPagar;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.fiscal.NotaSaida;
import com.kaneko.estacionamento.fiscal.NotaSaidaRepository;
import com.kaneko.estacionamento.fiscal.SituacaoNota;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.patio.Mensalista;
import com.kaneko.estacionamento.patio.MensalistaRepository;
import com.kaneko.estacionamento.patio.Movimentacao;
import com.kaneko.estacionamento.patio.MovimentacaoRepository;
import com.kaneko.estacionamento.patio.StatusMensalista;
import com.kaneko.estacionamento.patio.StatusMovimentacao;
import com.kaneko.estacionamento.patio.Tarifa;
import com.kaneko.estacionamento.patio.TarifaRepository;
import com.kaneko.estacionamento.patio.TipoMovimentacao;
import com.kaneko.estacionamento.patio.Veiculo;
import com.kaneko.estacionamento.patio.VeiculoRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao dos relatorios gerenciais (4.26–4.30), montando cenarios
 * controlados em cada modulo de origem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RelatorioControllerTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired private MockMvc mvc;
    @Autowired private VeiculoRepository veiculoRepository;
    @Autowired private TarifaRepository tarifaRepository;
    @Autowired private MovimentacaoRepository movimentacaoRepository;
    @Autowired private MensalistaRepository mensalistaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private MarcaRepository marcaRepository;
    @Autowired private UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private NotaSaidaRepository notaSaidaRepository;

    private Veiculo veiculo(String placa) {
        Veiculo v = new Veiculo();
        v.setPlaca(placa);
        v.setModelo("Modelo " + placa);
        return veiculoRepository.save(v);
    }

    private Cliente cliente(String doc) {
        Cliente c = new Cliente();
        c.setNome("Cliente " + doc);
        c.setDocumento(doc);
        return clienteRepository.save(c);
    }

    @Test
    void patioAtualListaAbertasComResumo() throws Exception {
        Tarifa tarifa = tarifaRepository.findAll().get(0);
        Movimentacao m = new Movimentacao();
        m.setVeiculo(veiculo("PAT1A11"));
        m.setTipo(TipoMovimentacao.AVULSO);
        m.setTarifa(tarifa);
        m.setDataEntrada(OffsetDateTime.now().minusHours(2));
        m.setStatus(StatusMovimentacao.ABERTO);
        movimentacaoRepository.save(m);

        mvc.perform(get("/api/relatorios/patio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.totalVeiculos").value(1))
                .andExpect(jsonPath("$.resumo.avulsos").value(1))
                .andExpect(jsonPath("$.itens[0].placa").value("PAT1A11"))
                .andExpect(jsonPath("$.itens[0].minutosPermanencia").value(org.hamcrest.Matchers.greaterThanOrEqualTo(100)));
    }

    @Test
    void faturamentoSomaSaidasEVendas() throws Exception {
        Tarifa tarifa = tarifaRepository.findAll().get(0);
        OffsetDateTime meioDiaHoje = LocalDate.now().atTime(12, 0).atZone(FUSO).toOffsetDateTime();
        Movimentacao m = new Movimentacao();
        m.setVeiculo(veiculo("FAT1A11"));
        m.setTipo(TipoMovimentacao.AVULSO);
        m.setTarifa(tarifa);
        m.setDataEntrada(meioDiaHoje.minusHours(3));
        m.setDataSaida(meioDiaHoje);
        m.setValorCobrado(new BigDecimal("20.00"));
        m.setStatus(StatusMovimentacao.FECHADO);
        movimentacaoRepository.save(m);

        NotaSaida nota = new NotaSaida();
        nota.setNumero("NFREL1");
        nota.setCliente(cliente("90000000001"));
        nota.setDataEmissao(LocalDate.now());
        nota.setValorTotal(new BigDecimal("50.00"));
        nota.setSituacao(SituacaoNota.CONFIRMADA);
        notaSaidaRepository.save(nota);

        String hoje = LocalDate.now().toString();
        mvc.perform(get("/api/relatorios/faturamento").param("inicio", hoje).param("fim", hoje))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estacionamento").value(20.00))
                .andExpect(jsonPath("$.vendas").value(50.00))
                .andExpect(jsonPath("$.totalFaturado").value(70.00));
    }

    @Test
    void faturamentoRejeitaPeriodoInvertido() throws Exception {
        mvc.perform(get("/api/relatorios/faturamento")
                        .param("inicio", "2026-06-20").param("fim", "2026-06-10"))
                .andExpect(status().isConflict());
    }

    @Test
    void contasAVencerListaTitulosEmAberto() throws Exception {
        Fornecedor f = new Fornecedor();
        f.setNome("Fornecedor Rel");
        f.setDocumento("11111111000111");
        f = fornecedorRepository.save(f);

        ContaPagar cp = new ContaPagar();
        cp.setFornecedor(f);
        cp.setValorOriginal(new BigDecimal("100.00"));
        cp.setValorTotal(new BigDecimal("100.00"));
        cp.setDataEmissao(LocalDate.now());
        cp.setDataVencimento(LocalDate.now().plusDays(10));
        cp.setSituacao(SituacaoContaPagar.PENDENTE);
        contaPagarRepository.save(cp);

        ContaReceber cr = new ContaReceber();
        cr.setCliente(cliente("90000000002"));
        cr.setValorOriginal(new BigDecimal("200.00"));
        cr.setValorTotal(new BigDecimal("200.00"));
        cr.setDataEmissao(LocalDate.now());
        cr.setDataVencimento(LocalDate.now().plusDays(5));
        cr.setSituacao(SituacaoContaReceber.PENDENTE);
        contaReceberRepository.save(cr);

        mvc.perform(get("/api/relatorios/contas-a-vencer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAPagar").value(100.00))
                .andExpect(jsonPath("$.totalAReceber").value(200.00))
                .andExpect(jsonPath("$.aPagar[0].contraparte").value("Fornecedor Rel"));
    }

    @Test
    void estoqueMinimoListaProdutosAbaixoDoMinimo() throws Exception {
        Marca marca = new Marca();
        marca.setNome("Marca Rel");
        marca = marcaRepository.save(marca);
        UnidadeMedida un = new UnidadeMedida();
        un.setNome("Unidade Rel");
        un.setSigla("UNR");
        un = unidadeMedidaRepository.save(un);

        Produto p = new Produto();
        p.setNome("Produto Baixo");
        p.setMarca(marca);
        p.setUnidadeMedida(un);
        p.setQuantidade(new BigDecimal("2.000"));
        p.setQuantidadeMinima(new BigDecimal("5.000"));
        produtoRepository.save(p);

        mvc.perform(get("/api/relatorios/estoque-minimo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].produto").value("Produto Baixo"));
    }

    @Test
    void mensalistasAtivosTotalizaReceita() throws Exception {
        Mensalista contrato = new Mensalista();
        contrato.setCliente(cliente("90000000003"));
        contrato.setVeiculo(veiculo("MEN1A11"));
        contrato.setValorMensal(new BigDecimal("250.00"));
        contrato.setDataInicio(LocalDate.now().minusMonths(1));
        contrato.setStatus(StatusMensalista.ATIVO);
        mensalistaRepository.save(contrato);

        mvc.perform(get("/api/relatorios/mensalistas-ativos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.receitaMensalRecorrente").value(250.00))
                .andExpect(jsonPath("$.itens[0].placa").value("MEN1A11"));
    }
}
