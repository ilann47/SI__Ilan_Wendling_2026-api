package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarRequest;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.FormaPagamento;
import com.kaneko.estacionamento.pagamento.FormaPagamentoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio dos titulos a pagar (lancamento manual, consulta e baixa).
 * Os titulos com origem em nota sao gerados pelo modulo fiscal.
 */
@Service
public class ContaPagarService {

    private final ContaPagarRepository repository;
    private final FornecedorRepository fornecedorRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final AuditoriaService auditoria;
    private final FinanceiroConsultaService consultas;
    private final BaixaFinanceiraService baixas;

    public ContaPagarService(ContaPagarRepository repository,
                             FornecedorRepository fornecedorRepository,
                             CondicaoPagamentoRepository condicaoPagamentoRepository,
                             FormaPagamentoRepository formaPagamentoRepository,
                             AuditoriaService auditoria, FinanceiroConsultaService consultas,
                             BaixaFinanceiraService baixas) {
        this.repository = repository;
        this.fornecedorRepository = fornecedorRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
        this.auditoria = auditoria;
        this.consultas = consultas;
        this.baixas = baixas;
    }

    @Transactional(readOnly = true)
    public Page<ContaPagar> listar(Long organizacaoId, Pageable pageable) {
        return listar(organizacaoId, com.kaneko.estacionamento.financeiro.dto.FinanceiroFiltro.vazio(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<ContaPagar> listar(Long org, com.kaneko.estacionamento.financeiro.dto.FinanceiroFiltro filtro, Pageable page) {
        return consultas.listar(ContaPagar.class, org, filtro, page);
    }

    @Transactional(readOnly = true)
    public com.kaneko.estacionamento.financeiro.dto.FinanceiroResumoResponse resumo(
            Long org, com.kaneko.estacionamento.financeiro.dto.FinanceiroFiltro filtro) {
        return consultas.resumo(ContaPagar.class, org, filtro);
    }

    @Transactional(readOnly = true)
    public java.util.List<com.kaneko.estacionamento.financeiro.dto.BaixaFinanceiraResponse> historico(Long org, Long id) {
        buscar(org, id);
        return baixas.historico(true, id);
    }

    @Transactional(readOnly = true)
    public ContaPagar buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaPagar", id));
    }

    @Transactional
    public ContaPagar criar(Long organizacaoId, ContaPagarRequest req) {
        ContaPagar conta = new ContaPagar();
        conta.setOrganizacaoId(organizacaoId);
        Fornecedor fornecedor = (organizacaoId == null
                ? fornecedorRepository.findByIdAndOrganizacaoIdIsNull(req.fornecedorId())
                : fornecedorRepository.findByIdAndOrganizacaoId(req.fornecedorId(), organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", req.fornecedorId()));
        conta.setFornecedor(fornecedor);
        if (req.condicaoPagamentoId() != null) {
            conta.setCondicaoPagamento(buscarCondicao(organizacaoId, req.condicaoPagamentoId()));
        }
        if (req.formaPagamentoId() != null) {
            conta.setFormaPagamento(buscarForma(organizacaoId, req.formaPagamentoId()));
        }
        conta.setNumeroParcela(req.numeroParcela() != null ? req.numeroParcela() : 1);
        conta.setTotalParcelas(req.totalParcelas() != null ? req.totalParcelas() : 1);
        if (conta.getNumeroParcela() > conta.getTotalParcelas()) {
            throw new ConflitoException("O número da parcela não pode exceder o total de parcelas.");
        }
        BigDecimal original = req.valorOriginal();
        BigDecimal desconto = nz(req.valorDesconto());
        BigDecimal juros = nz(req.valorJuros());
        BigDecimal multa = nz(req.valorMulta());
        conta.setValorOriginal(original);
        conta.setValorDesconto(desconto);
        conta.setValorJuros(juros);
        conta.setValorMulta(multa);
        conta.setValorTotal(FinanceiroRegras.total(original, desconto, juros, multa, req.valorTotal()));
        conta.setValorPago(BigDecimal.ZERO);
        LocalDate emissao = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        conta.setDataEmissao(emissao);
        if (req.dataVencimento().isBefore(emissao)) {
            throw new ConflitoException("A data de vencimento não pode ser anterior à data de emissão.");
        }
        conta.setDataVencimento(req.dataVencimento());
        conta.setSituacao(SituacaoContaPagar.PENDENTE);
        conta.setObservacao(req.observacao());
        conta = repository.saveAndFlush(conta);
        auditar(conta, "CONTA_PAGAR_CRIADA", null, snapshot(conta));
        return conta;
    }

    /** Registra um pagamento (baixa). Acumula o valor pago e atualiza a situacao. */
    @Transactional
    public ContaPagar baixar(Long organizacaoId, Long id, BaixaRequest req) {
        return baixar(organizacaoId, id, req, null);
    }

    @Transactional
    public ContaPagar baixar(Long organizacaoId, Long id, BaixaRequest req, String chave) {
        FinanceiroRegras.validarBaixa(req.valor());
        ContaPagar conta = repository.buscarParaBaixa(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaPagar", id));
        if (baixas.repetida(true, id, chave, req)) return conta;
        if (conta.getSituacao() == SituacaoContaPagar.PAGA
                || conta.getSituacao() == SituacaoContaPagar.CANCELADA) {
            throw new ConflitoException("Este título não pode receber baixa (já está pago ou cancelado).");
        }
        Map<String, Object> anterior = snapshot(conta);
        BigDecimal novoPago = conta.getValorPago().add(req.valor());
        if (novoPago.compareTo(conta.getValorTotal()) > 0) {
            throw new ConflitoException("O valor da baixa excede o saldo devedor do título.");
        }
        conta.setValorPago(novoPago);
        if (novoPago.compareTo(conta.getValorTotal()) == 0) {
            conta.setSituacao(SituacaoContaPagar.PAGA);
            conta.setDataPagamento(req.data() != null ? req.data() : LocalDate.now());
        } else {
            conta.setSituacao(SituacaoContaPagar.PARCIAL);
        }
        conta = repository.saveAndFlush(conta);
        auditar(conta, "CONTA_PAGAR_BAIXADA", anterior, snapshot(conta));
        baixas.registrar(true, organizacaoId, id, chave, req);
        return conta;
    }

    @Transactional
    public ContaPagar cancelar(Long organizacaoId, Long id) {
        ContaPagar conta = repository.buscarParaBaixa(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaPagar", id));
        if (conta.getValorPago().signum() > 0) {
            throw new ConflitoException("Um título com baixa não pode ser cancelado; os lançamentos devem ser preservados.");
        }
        if (conta.getSituacao() == SituacaoContaPagar.CANCELADA) return conta;
        Map<String, Object> anterior = snapshot(conta);
        conta.setSituacao(SituacaoContaPagar.CANCELADA);
        conta = repository.saveAndFlush(conta);
        auditar(conta, "CONTA_PAGAR_CANCELADA", anterior, snapshot(conta));
        return conta;
    }

    private CondicaoPagamento buscarCondicao(Long organizacaoId, Long id) {
        return (organizacaoId == null
                ? condicaoPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                : condicaoPagamentoRepository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private FormaPagamento buscarForma(Long organizacaoId, Long id) {
        return (organizacaoId == null
                ? formaPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                : formaPagamentoRepository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("FormaPagamento", id));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void auditar(ContaPagar conta, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (conta.getOrganizacaoId() == null) {
            return;
        }
        auditoria.registrar(new RegistrarAuditoriaCommand(conta.getOrganizacaoId(), acao,
                "CONTA_PAGAR", conta.getId(), null, null, null, anterior, posterior,
                null, conta.getVersion(), ClasseRetencaoAuditoria.FINANCEIRA));
    }

    private static Map<String, Object> snapshot(ContaPagar conta) {
        return Map.of("situacao", conta.getSituacao().name(),
                "fornecedorId", conta.getFornecedor().getId(),
                "valorTotal", conta.getValorTotal(),
                "valorPago", conta.getValorPago());
    }
}
