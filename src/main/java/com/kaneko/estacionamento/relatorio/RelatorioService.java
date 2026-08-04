package com.kaneko.estacionamento.relatorio;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.financeiro.ContaPagar;
import com.kaneko.estacionamento.financeiro.ContaPagarAvulsa;
import com.kaneko.estacionamento.financeiro.ContaPagarAvulsaRepository;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaPagar;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.fiscal.NotaSaida;
import com.kaneko.estacionamento.fiscal.NotaSaidaRepository;
import com.kaneko.estacionamento.fiscal.NotaServico;
import com.kaneko.estacionamento.fiscal.NotaServicoRepository;
import com.kaneko.estacionamento.fiscal.SituacaoNota;
import com.kaneko.estacionamento.fiscal.SituacaoNotaServico;
import com.kaneko.estacionamento.patio.Mensalista;
import com.kaneko.estacionamento.patio.MensalistaRepository;
import com.kaneko.estacionamento.patio.Movimentacao;
import com.kaneko.estacionamento.patio.MovimentacaoRepository;
import com.kaneko.estacionamento.patio.StatusMensalista;
import com.kaneko.estacionamento.patio.StatusMovimentacao;
import com.kaneko.estacionamento.patio.TipoMovimentacao;
import com.kaneko.estacionamento.relatorio.dto.ContasAVencerResponse;
import com.kaneko.estacionamento.relatorio.dto.EstoqueMinimoResponse;
import com.kaneko.estacionamento.relatorio.dto.FaturamentoDiaResponse;
import com.kaneko.estacionamento.relatorio.dto.MensalistasAtivosResponse;
import com.kaneko.estacionamento.relatorio.dto.PatioAtualResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatorios gerenciais (consultas consolidadas, somente leitura) — secoes
 * 4.26 a 4.30 dos requisitos funcionais.
 */
@Service
@Transactional(readOnly = true)
public class RelatorioService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final List<SituacaoContaPagar> ABERTAS_PAGAR =
            List.of(SituacaoContaPagar.PENDENTE, SituacaoContaPagar.PARCIAL);
    private static final List<SituacaoContaReceber> ABERTAS_RECEBER =
            List.of(SituacaoContaReceber.PENDENTE, SituacaoContaReceber.PARCIAL);

    private final MovimentacaoRepository movimentacaoRepository;
    private final NotaSaidaRepository notaSaidaRepository;
    private final NotaServicoRepository notaServicoRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final ContaPagarAvulsaRepository contaPagarAvulsaRepository;
    private final ProdutoRepository produtoRepository;
    private final MensalistaRepository mensalistaRepository;

    public RelatorioService(MovimentacaoRepository movimentacaoRepository,
                            NotaSaidaRepository notaSaidaRepository,
                            NotaServicoRepository notaServicoRepository,
                            ContaReceberRepository contaReceberRepository,
                            ContaPagarRepository contaPagarRepository,
                            ContaPagarAvulsaRepository contaPagarAvulsaRepository,
                            ProdutoRepository produtoRepository,
                            MensalistaRepository mensalistaRepository) {
        this.movimentacaoRepository = movimentacaoRepository;
        this.notaSaidaRepository = notaSaidaRepository;
        this.notaServicoRepository = notaServicoRepository;
        this.contaReceberRepository = contaReceberRepository;
        this.contaPagarRepository = contaPagarRepository;
        this.contaPagarAvulsaRepository = contaPagarAvulsaRepository;
        this.produtoRepository = produtoRepository;
        this.mensalistaRepository = mensalistaRepository;
    }

    /** 4.26 — Patio atual. */
    public PatioAtualResponse patioAtual() {
        List<Movimentacao> abertas =
                movimentacaoRepository.findByStatusOrderByDataEntradaAsc(StatusMovimentacao.ABERTO);
        OffsetDateTime agora = OffsetDateTime.now();
        List<PatioAtualResponse.ItemPatio> itens = abertas.stream()
                .map(m -> new PatioAtualResponse.ItemPatio(
                        m.getId(),
                        m.getVeiculo().getPlaca(),
                        m.getVeiculo().getModelo(),
                        m.getTipo().name(),
                        m.getDataEntrada(),
                        Math.max(0, Duration.between(m.getDataEntrada(), agora).toMinutes())))
                .toList();
        long avulsos = abertas.stream().filter(m -> m.getTipo() == TipoMovimentacao.AVULSO).count();
        long mensalistas = abertas.size() - avulsos;
        return new PatioAtualResponse(itens,
                new PatioAtualResponse.ResumoPatio(abertas.size(), avulsos, mensalistas));
    }

    /** 4.27 — Faturamento do dia/periodo. */
    public FaturamentoDiaResponse faturamento(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        OffsetDateTime ini = inicio.atStartOfDay(FUSO).toOffsetDateTime();
        OffsetDateTime fimDia = fim.atTime(LocalTime.MAX).atZone(FUSO).toOffsetDateTime();

        BigDecimal estacionamento = movimentacaoRepository
                .findByStatusAndDataSaidaBetween(StatusMovimentacao.FECHADO, ini, fimDia).stream()
                .map(m -> nz(m.getValorCobrado())).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal vendas = notaSaidaRepository
                .findBySituacaoAndDataEmissaoBetween(SituacaoNota.CONFIRMADA, inicio, fim).stream()
                .map(NotaSaida::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal servicos = notaServicoRepository
                .findBySituacaoAndDataEmissaoBetween(SituacaoNotaServico.EMITIDA, inicio, fim).stream()
                .map(NotaServico::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal recebimentos = contaReceberRepository
                .findByDataRecebimentoBetween(inicio, fim).stream()
                .map(ContaReceber::getValorRecebido).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFaturado = estacionamento.add(vendas).add(servicos);
        return new FaturamentoDiaResponse(inicio, fim, estacionamento, vendas, servicos,
                totalFaturado, recebimentos);
    }

    /** 4.28 — Contas a vencer (a pagar e a receber em aberto). */
    public ContasAVencerResponse contasAVencer(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        LocalDate hoje = LocalDate.now();

        List<ContasAVencerResponse.Titulo> aPagar = new ArrayList<>();
        for (ContaPagar c : contaPagarRepository
                .findByOrganizacaoIdIsNullAndSituacaoInAndDataVencimentoBetween(
                        ABERTAS_PAGAR, inicio, fim)) {
            aPagar.add(new ContasAVencerResponse.Titulo("CONTA_PAGAR", c.getId(),
                    c.getFornecedor().getNome(), c.getDataVencimento(),
                    c.getValorTotal().subtract(c.getValorPago()),
                    c.getDataVencimento().isBefore(hoje), c.getSituacao().name()));
        }
        for (ContaPagarAvulsa c : contaPagarAvulsaRepository
                .findBySituacaoInAndDataVencimentoBetween(ABERTAS_PAGAR, inicio, fim)) {
            String contraparte = c.getFornecedor() != null ? c.getFornecedor().getNome() : c.getDescricao();
            aPagar.add(new ContasAVencerResponse.Titulo("DESPESA_AVULSA", c.getId(),
                    contraparte, c.getDataVencimento(),
                    c.getValorTotal().subtract(c.getValorPago()),
                    c.getDataVencimento().isBefore(hoje), c.getSituacao().name()));
        }
        aPagar.sort(Comparator.comparing(ContasAVencerResponse.Titulo::vencimento));

        List<ContasAVencerResponse.Titulo> aReceber = new ArrayList<>();
        for (ContaReceber c : contaReceberRepository
                .findBySituacaoInAndDataVencimentoBetween(ABERTAS_RECEBER, inicio, fim)) {
            aReceber.add(new ContasAVencerResponse.Titulo("CONTA_RECEBER", c.getId(),
                    c.getCliente().getNome(), c.getDataVencimento(),
                    c.getValorTotal().subtract(c.getValorRecebido()),
                    c.getDataVencimento().isBefore(hoje), c.getSituacao().name()));
        }
        aReceber.sort(Comparator.comparing(ContasAVencerResponse.Titulo::vencimento));

        return new ContasAVencerResponse(inicio, fim, aPagar, aReceber,
                somaSaldo(aPagar, false), somaSaldo(aReceber, false),
                somaSaldo(aPagar, true), somaSaldo(aReceber, true));
    }

    /** 4.29 — Estoque minimo. */
    public EstoqueMinimoResponse estoqueMinimo() {
        List<Produto> produtos = produtoRepository.findComEstoqueAbaixoDoMinimo();
        List<EstoqueMinimoResponse.ItemEstoque> itens = produtos.stream()
                .map(p -> new EstoqueMinimoResponse.ItemEstoque(
                        p.getId(),
                        p.getNome(),
                        p.getMarca().getNome(),
                        p.getCategoria() != null ? p.getCategoria().getNome() : null,
                        p.getQuantidade(),
                        p.getQuantidadeMinima()))
                .toList();
        return new EstoqueMinimoResponse(itens.size(), itens);
    }

    /** 4.30 — Mensalistas ativos. */
    public MensalistasAtivosResponse mensalistasAtivos() {
        List<Mensalista> ativos =
                mensalistaRepository.findByStatusOrderByDataInicioAsc(StatusMensalista.ATIVO);
        List<MensalistasAtivosResponse.ItemMensalista> itens = ativos.stream()
                .map(m -> new MensalistasAtivosResponse.ItemMensalista(
                        m.getId(),
                        m.getCliente().getNome(),
                        m.getVeiculo().getPlaca(),
                        m.getVeiculo().getModelo(),
                        m.getValorMensal(),
                        m.getDataInicio()))
                .toList();
        BigDecimal receita = ativos.stream()
                .map(Mensalista::getValorMensal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MensalistasAtivosResponse(itens.size(), receita, itens);
    }

    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new ConflitoException("A data final não pode ser anterior à inicial.");
        }
    }

    private static BigDecimal somaSaldo(List<ContasAVencerResponse.Titulo> titulos, boolean apenasVencidos) {
        return titulos.stream()
                .filter(t -> !apenasVencidos || t.vencido())
                .map(ContasAVencerResponse.Titulo::saldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
