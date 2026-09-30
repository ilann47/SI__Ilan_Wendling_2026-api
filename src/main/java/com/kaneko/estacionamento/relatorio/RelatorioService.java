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
import com.kaneko.estacionamento.estoque.EstoqueMinimoTenantView;
import com.kaneko.estacionamento.estoque.SaldoEstoqueRepository;
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
    private final SaldoEstoqueRepository saldoEstoqueRepository;

    public RelatorioService(MovimentacaoRepository movimentacaoRepository,
                            NotaSaidaRepository notaSaidaRepository,
                            NotaServicoRepository notaServicoRepository,
                            ContaReceberRepository contaReceberRepository,
                            ContaPagarRepository contaPagarRepository,
                            ContaPagarAvulsaRepository contaPagarAvulsaRepository,
                            ProdutoRepository produtoRepository,
                            MensalistaRepository mensalistaRepository,
                            SaldoEstoqueRepository saldoEstoqueRepository) {
        this.movimentacaoRepository = movimentacaoRepository;
        this.notaSaidaRepository = notaSaidaRepository;
        this.notaServicoRepository = notaServicoRepository;
        this.contaReceberRepository = contaReceberRepository;
        this.contaPagarRepository = contaPagarRepository;
        this.contaPagarAvulsaRepository = contaPagarAvulsaRepository;
        this.produtoRepository = produtoRepository;
        this.mensalistaRepository = mensalistaRepository;
        this.saldoEstoqueRepository = saldoEstoqueRepository;
    }

    /** 4.26 — Patio atual. */
    public PatioAtualResponse patioAtual(Long organizacaoId) {
        List<Movimentacao> abertas = organizacaoId == null
                ? movimentacaoRepository.findByStatusOrderByDataEntradaAsc(StatusMovimentacao.ABERTO)
                : movimentacaoRepository.findByOrganizacaoIdAndStatusOrderByDataEntradaAsc(
                        organizacaoId, StatusMovimentacao.ABERTO);
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
    public FaturamentoDiaResponse faturamento(Long organizacaoId, LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        OffsetDateTime ini = inicio.atStartOfDay(FUSO).toOffsetDateTime();
        OffsetDateTime fimDia = fim.atTime(LocalTime.MAX).atZone(FUSO).toOffsetDateTime();

        List<Movimentacao> movimentos = organizacaoId == null
                ? movimentacaoRepository.findByOrganizacaoIdIsNullAndStatusAndDataSaidaBetween(
                        StatusMovimentacao.FECHADO, ini, fimDia)
                : movimentacaoRepository.findByOrganizacaoIdAndStatusAndDataSaidaBetween(
                        organizacaoId, StatusMovimentacao.FECHADO, ini, fimDia);
        BigDecimal estacionamento = movimentos.stream()
                .map(m -> nz(m.getValorCobrado())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<NotaSaida> notasSaida = organizacaoId == null
                ? notaSaidaRepository.findByOrganizacaoIdIsNullAndSituacaoAndDataEmissaoBetween(
                        SituacaoNota.CONFIRMADA, inicio, fim)
                : notaSaidaRepository.findByOrganizacaoIdAndSituacaoAndDataEmissaoBetween(
                        organizacaoId, SituacaoNota.CONFIRMADA, inicio, fim);
        BigDecimal vendas = notasSaida.stream()
                .map(NotaSaida::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<NotaServico> notasServico = organizacaoId == null
                ? notaServicoRepository.findByOrganizacaoIdIsNullAndSituacaoAndDataEmissaoBetween(
                        SituacaoNotaServico.EMITIDA, inicio, fim)
                : notaServicoRepository.findByOrganizacaoIdAndSituacaoAndDataEmissaoBetween(
                        organizacaoId, SituacaoNotaServico.EMITIDA, inicio, fim);
        BigDecimal servicos = notasServico.stream()
                .map(NotaServico::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ContaReceber> contasRecebidas = organizacaoId == null
                ? contaReceberRepository.findByOrganizacaoIdIsNullAndDataRecebimentoBetween(inicio, fim)
                : contaReceberRepository.findByOrganizacaoIdAndDataRecebimentoBetween(
                        organizacaoId, inicio, fim);
        BigDecimal recebimentos = contasRecebidas.stream()
                .map(ContaReceber::getValorRecebido).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFaturado = estacionamento.add(vendas).add(servicos);
        return new FaturamentoDiaResponse(inicio, fim, estacionamento, vendas, servicos,
                totalFaturado, recebimentos);
    }

    /** 4.28 — Contas a vencer (a pagar e a receber em aberto). */
    public ContasAVencerResponse contasAVencer(Long organizacaoId, LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        LocalDate hoje = LocalDate.now();

        List<ContasAVencerResponse.Titulo> aPagar = new ArrayList<>();
        List<ContaPagar> contasPagar = organizacaoId == null
                ? contaPagarRepository.findByOrganizacaoIdIsNullAndSituacaoInAndDataVencimentoBetween(
                        ABERTAS_PAGAR, inicio, fim)
                : contaPagarRepository.findByOrganizacaoIdAndSituacaoInAndDataVencimentoBetween(
                        organizacaoId, ABERTAS_PAGAR, inicio, fim);
        for (ContaPagar c : contasPagar) {
            aPagar.add(new ContasAVencerResponse.Titulo("CONTA_PAGAR", c.getId(),
                    c.getFornecedor().getNome(), c.getDataVencimento(),
                    c.getValorTotal().subtract(c.getValorPago()),
                    c.getDataVencimento().isBefore(hoje), c.getSituacao().name()));
        }
        List<ContaPagarAvulsa> despesas = organizacaoId == null
                ? contaPagarAvulsaRepository.findBySituacaoInAndDataVencimentoBetween(
                        ABERTAS_PAGAR, inicio, fim)
                : contaPagarAvulsaRepository.findByOrganizacaoIdAndSituacaoInAndDataVencimentoBetween(
                        organizacaoId, ABERTAS_PAGAR, inicio, fim);
        for (ContaPagarAvulsa c : despesas) {
            String contraparte = c.getFornecedor() != null ? c.getFornecedor().getNome() : c.getDescricao();
            aPagar.add(new ContasAVencerResponse.Titulo("DESPESA_AVULSA", c.getId(),
                    contraparte, c.getDataVencimento(),
                    c.getValorTotal().subtract(c.getValorPago()),
                    c.getDataVencimento().isBefore(hoje), c.getSituacao().name()));
        }
        aPagar.sort(Comparator.comparing(ContasAVencerResponse.Titulo::vencimento));

        List<ContasAVencerResponse.Titulo> aReceber = new ArrayList<>();
        List<ContaReceber> contasReceber = organizacaoId == null
                ? contaReceberRepository.findByOrganizacaoIdIsNullAndSituacaoInAndDataVencimentoBetween(
                        ABERTAS_RECEBER, inicio, fim)
                : contaReceberRepository.findByOrganizacaoIdAndSituacaoInAndDataVencimentoBetween(
                        organizacaoId, ABERTAS_RECEBER, inicio, fim);
        for (ContaReceber c : contasReceber) {
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
    public EstoqueMinimoResponse estoqueMinimo(Long organizacaoId) {
        if (organizacaoId != null) {
            List<EstoqueMinimoTenantView> posicoes = saldoEstoqueRepository
                    .listarEstoqueMinimo(organizacaoId);
            List<EstoqueMinimoResponse.ItemEstoque> itens = posicoes.stream()
                    .map(p -> new EstoqueMinimoResponse.ItemEstoque(p.getProdutoId(),
                            p.getProduto(), p.getMarca(), p.getCategoria(),
                            p.getQuantidade(), p.getQuantidadeMinima()))
                    .toList();
            return new EstoqueMinimoResponse(itens.size(), itens);
        }
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
    public MensalistasAtivosResponse mensalistasAtivos(Long organizacaoId) {
        List<Mensalista> ativos = organizacaoId == null
                ? mensalistaRepository.findByStatusOrderByDataInicioAsc(StatusMensalista.ATIVO)
                : mensalistaRepository.findByOrganizacaoIdAndStatusOrderByDataInicioAsc(
                        organizacaoId, StatusMensalista.ATIVO);
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
