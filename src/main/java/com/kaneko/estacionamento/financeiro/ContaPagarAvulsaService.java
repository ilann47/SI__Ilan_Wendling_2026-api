package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarAvulsaRequest;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.pagamento.FormaPagamento;
import com.kaneko.estacionamento.pagamento.FormaPagamentoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/**
 * Regras de negocio da despesa avulsa a pagar, incluindo a baixa (pagamento).
 */
@Service
public class ContaPagarAvulsaService {

    private final ContaPagarAvulsaRepository repository;
    private final FornecedorRepository fornecedorRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final AuditoriaService auditoria;

    public ContaPagarAvulsaService(ContaPagarAvulsaRepository repository,
                                   FornecedorRepository fornecedorRepository,
                                   FormaPagamentoRepository formaPagamentoRepository,
                                   AuditoriaService auditoria) {
        this.repository = repository;
        this.fornecedorRepository = fornecedorRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<ContaPagarAvulsa> listar(Long organizacaoId, Pageable pageable) {
        return organizacaoId == null ? repository.findAllByOrganizacaoIdIsNull(pageable)
                : repository.findAllByOrganizacaoId(organizacaoId, pageable);
    }

    @Transactional(readOnly = true)
    public ContaPagarAvulsa buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaPagarAvulsa", id));
    }

    @Transactional
    public ContaPagarAvulsa criar(Long organizacaoId, ContaPagarAvulsaRequest req) {
        ContaPagarAvulsa conta = new ContaPagarAvulsa();
        conta.setOrganizacaoId(organizacaoId);
        aplicar(organizacaoId, conta, req);
        conta.setValorPago(BigDecimal.ZERO);
        conta.setSituacao(SituacaoContaPagar.PENDENTE);
        conta = repository.saveAndFlush(conta);
        auditar(conta, "DESPESA_AVULSA_CRIADA", null, snapshot(conta));
        return conta;
    }

    @Transactional
    public ContaPagarAvulsa atualizar(Long organizacaoId, Long id, ContaPagarAvulsaRequest req) {
        ContaPagarAvulsa conta = buscar(organizacaoId, id);
        if (conta.getSituacao() != SituacaoContaPagar.PENDENTE) {
            throw new ConflitoException("Apenas despesas pendentes podem ser alteradas.");
        }
        Map<String, Object> anterior = snapshot(conta);
        aplicar(organizacaoId, conta, req);
        conta = repository.saveAndFlush(conta);
        auditar(conta, "DESPESA_AVULSA_ATUALIZADA", anterior, snapshot(conta));
        return conta;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        ContaPagarAvulsa conta = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(conta);
        repository.delete(conta);
        repository.flush();
        auditar(conta, "DESPESA_AVULSA_EXCLUIDA", anterior, anterior);
    }

    /** Registra um pagamento (baixa). Acumula o valor pago e atualiza a situacao. */
    @Transactional
    public ContaPagarAvulsa baixar(Long organizacaoId, Long id, BaixaRequest req) {
        ContaPagarAvulsa conta = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(conta);
        if (conta.getSituacao() == SituacaoContaPagar.PAGA
                || conta.getSituacao() == SituacaoContaPagar.CANCELADA) {
            throw new ConflitoException("Esta despesa não pode receber baixa (já está paga ou cancelada).");
        }
        BigDecimal novoPago = conta.getValorPago().add(req.valor());
        if (novoPago.compareTo(conta.getValorTotal()) > 0) {
            throw new ConflitoException("O valor da baixa excede o saldo devedor da despesa.");
        }
        conta.setValorPago(novoPago);
        if (novoPago.compareTo(conta.getValorTotal()) == 0) {
            conta.setSituacao(SituacaoContaPagar.PAGA);
            conta.setDataPagamento(req.data() != null ? req.data() : LocalDate.now());
        } else {
            conta.setSituacao(SituacaoContaPagar.PARCIAL);
        }
        conta = repository.saveAndFlush(conta);
        auditar(conta, "DESPESA_AVULSA_BAIXADA", anterior, snapshot(conta));
        return conta;
    }

    @Transactional
    public ContaPagarAvulsa cancelar(Long organizacaoId, Long id) {
        ContaPagarAvulsa conta = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(conta);
        if (conta.getSituacao() == SituacaoContaPagar.PAGA) {
            throw new ConflitoException("Uma despesa paga não pode ser cancelada.");
        }
        conta.setSituacao(SituacaoContaPagar.CANCELADA);
        conta = repository.saveAndFlush(conta);
        auditar(conta, "DESPESA_AVULSA_CANCELADA", anterior, snapshot(conta));
        return conta;
    }

    private void aplicar(Long organizacaoId, ContaPagarAvulsa conta, ContaPagarAvulsaRequest req) {
        conta.setDescricao(req.descricao().trim());
        conta.setFornecedor(req.fornecedorId() != null
                ? buscarFornecedor(organizacaoId, req.fornecedorId()) : null);
        conta.setFormaPagamento(req.formaPagamentoId() != null
                ? buscarForma(organizacaoId, req.formaPagamentoId()) : null);
        conta.setNumeroNota(req.numeroNota());
        conta.setModelo(req.modelo());
        conta.setSerie(req.serie());
        conta.setNumeroParcela(req.numeroParcela() != null ? req.numeroParcela() : 1);
        conta.setTotalParcelas(req.totalParcelas() != null ? req.totalParcelas() : 1);
        BigDecimal original = req.valorOriginal();
        BigDecimal desconto = req.valorDesconto() != null ? req.valorDesconto() : BigDecimal.ZERO;
        BigDecimal juros = req.valorJuros() != null ? req.valorJuros() : BigDecimal.ZERO;
        BigDecimal multa = req.valorMulta() != null ? req.valorMulta() : BigDecimal.ZERO;
        conta.setValorOriginal(original);
        conta.setValorDesconto(desconto);
        conta.setValorJuros(juros);
        conta.setValorMulta(multa);
        conta.setValorTotal(req.valorTotal() != null
                ? req.valorTotal()
                : original.subtract(desconto).add(juros).add(multa));
        LocalDate emissao = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        conta.setDataEmissao(emissao);
        if (req.dataVencimento().isBefore(emissao)) {
            throw new ConflitoException("A data de vencimento não pode ser anterior à data de emissão.");
        }
        conta.setDataVencimento(req.dataVencimento());
        conta.setObservacao(req.observacao());
    }

    private Fornecedor buscarFornecedor(Long organizacaoId, Long id) {
        return (organizacaoId == null ? fornecedorRepository.findByIdAndOrganizacaoIdIsNull(id)
                : fornecedorRepository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", id));
    }

    private FormaPagamento buscarForma(Long organizacaoId, Long id) {
        return (organizacaoId == null ? formaPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                : formaPagamentoRepository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("FormaPagamento", id));
    }

    private void auditar(ContaPagarAvulsa conta, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (conta.getOrganizacaoId() == null) return;
        auditoria.registrar(new RegistrarAuditoriaCommand(conta.getOrganizacaoId(), acao,
                "DESPESA_AVULSA", conta.getId(), null, null, null, anterior, posterior,
                null, conta.getVersion(), ClasseRetencaoAuditoria.FINANCEIRA));
    }

    private static Map<String, Object> snapshot(ContaPagarAvulsa conta) {
        return Map.of("descricao", conta.getDescricao(), "valorTotal", conta.getValorTotal(),
                "valorPago", conta.getValorPago(), "situacao", conta.getSituacao().name());
    }
}
