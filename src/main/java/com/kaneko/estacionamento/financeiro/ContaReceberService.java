package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberRequest;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
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
 * Regras de negocio dos titulos a receber (lancamento manual, consulta e baixa).
 * Os titulos com origem em nota de saida sao gerados pelo modulo fiscal.
 */
@Service
public class ContaReceberService {

    private final ContaReceberRepository repository;
    private final ClienteRepository clienteRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final AuditoriaService auditoria;
    private final FinanceiroConsultaService consultas;
    private final BaixaFinanceiraService baixas;

    public ContaReceberService(ContaReceberRepository repository,
                               ClienteRepository clienteRepository,
                               CondicaoPagamentoRepository condicaoPagamentoRepository,
                               FormaPagamentoRepository formaPagamentoRepository,
                             AuditoriaService auditoria, FinanceiroConsultaService consultas,
                             BaixaFinanceiraService baixas) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
        this.auditoria = auditoria;
        this.consultas = consultas;
        this.baixas = baixas;
    }

    @Transactional(readOnly = true)
    public Page<ContaReceber> listar(Long organizacaoId, Pageable pageable) {
        return listar(organizacaoId, com.kaneko.estacionamento.financeiro.dto.FinanceiroFiltro.vazio(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<ContaReceber> listar(Long org, com.kaneko.estacionamento.financeiro.dto.FinanceiroFiltro filtro, Pageable page) {
        return consultas.listar(ContaReceber.class, org, filtro, page);
    }

    @Transactional(readOnly = true)
    public com.kaneko.estacionamento.financeiro.dto.FinanceiroResumoResponse resumo(
            Long org, com.kaneko.estacionamento.financeiro.dto.FinanceiroFiltro filtro) {
        return consultas.resumo(ContaReceber.class, org, filtro);
    }

    @Transactional(readOnly = true)
    public java.util.List<com.kaneko.estacionamento.financeiro.dto.BaixaFinanceiraResponse> historico(Long org, Long id) {
        buscar(org, id);
        return baixas.historico(false, id);
    }

    @Transactional(readOnly = true)
    public ContaReceber buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaReceber", id));
    }

    @Transactional
    public ContaReceber criar(Long organizacaoId, ContaReceberRequest req) {
        ContaReceber conta = new ContaReceber();
        conta.setOrganizacaoId(organizacaoId);
        Cliente cliente = (organizacaoId == null
                ? clienteRepository.findByIdAndOrganizacaoIdIsNull(req.clienteId())
                : clienteRepository.findByIdAndOrganizacaoId(req.clienteId(), organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", req.clienteId()));
        conta.setCliente(cliente);
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
        conta.setValorRecebido(BigDecimal.ZERO);
        LocalDate emissao = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        conta.setDataEmissao(emissao);
        if (req.dataVencimento().isBefore(emissao)) {
            throw new ConflitoException("A data de vencimento não pode ser anterior à data de emissão.");
        }
        conta.setDataVencimento(req.dataVencimento());
        conta.setSituacao(SituacaoContaReceber.PENDENTE);
        conta.setObservacao(req.observacao());
        conta = repository.saveAndFlush(conta);
        auditar(conta, "CONTA_RECEBER_CRIADA", null, snapshot(conta));
        return conta;
    }

    /** Registra um recebimento (baixa). Acumula o valor recebido e atualiza a situacao. */
    @Transactional
    public ContaReceber baixar(Long organizacaoId, Long id, BaixaRequest req) {
        return baixar(organizacaoId, id, req, null);
    }

    @Transactional
    public ContaReceber baixar(Long organizacaoId, Long id, BaixaRequest req, String chave) {
        FinanceiroRegras.validarBaixa(req.valor());
        ContaReceber conta = repository.buscarParaBaixa(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaReceber", id));
        if (baixas.repetida(false, id, chave, req)) return conta;
        if (conta.getSituacao() == SituacaoContaReceber.RECEBIDA
                || conta.getSituacao() == SituacaoContaReceber.CANCELADA) {
            throw new ConflitoException("Este título não pode receber baixa (já está recebido ou cancelado).");
        }
        Map<String, Object> anterior = snapshot(conta);
        BigDecimal novoRecebido = conta.getValorRecebido().add(req.valor());
        if (novoRecebido.compareTo(conta.getValorTotal()) > 0) {
            throw new ConflitoException("O valor da baixa excede o saldo a receber do título.");
        }
        conta.setValorRecebido(novoRecebido);
        if (novoRecebido.compareTo(conta.getValorTotal()) == 0) {
            conta.setSituacao(SituacaoContaReceber.RECEBIDA);
            conta.setDataRecebimento(req.data() != null ? req.data() : LocalDate.now());
        } else {
            conta.setSituacao(SituacaoContaReceber.PARCIAL);
        }
        conta = repository.saveAndFlush(conta);
        auditar(conta, "CONTA_RECEBER_BAIXADA", anterior, snapshot(conta));
        baixas.registrar(false, organizacaoId, id, chave, req);
        return conta;
    }

    @Transactional
    public ContaReceber cancelar(Long organizacaoId, Long id) {
        ContaReceber conta = repository.buscarParaBaixa(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaReceber", id));
        if (conta.getValorRecebido().signum() > 0) {
            throw new ConflitoException("Um título com baixa não pode ser cancelado; os lançamentos devem ser preservados.");
        }
        if (conta.getSituacao() == SituacaoContaReceber.CANCELADA) return conta;
        Map<String, Object> anterior = snapshot(conta);
        conta.setSituacao(SituacaoContaReceber.CANCELADA);
        conta = repository.saveAndFlush(conta);
        auditar(conta, "CONTA_RECEBER_CANCELADA", anterior, snapshot(conta));
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

    private void auditar(ContaReceber conta, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (conta.getOrganizacaoId() == null) return;
        auditoria.registrar(new RegistrarAuditoriaCommand(conta.getOrganizacaoId(), acao,
                "CONTA_RECEBER", conta.getId(), null, null, null, anterior, posterior,
                null, conta.getVersion(), ClasseRetencaoAuditoria.FINANCEIRA));
    }

    private static Map<String, Object> snapshot(ContaReceber conta) {
        return Map.of("situacao", conta.getSituacao().name(),
                "clienteId", conta.getCliente().getId(),
                "valorTotal", conta.getValorTotal(),
                "valorRecebido", conta.getValorRecebido());
    }
}
