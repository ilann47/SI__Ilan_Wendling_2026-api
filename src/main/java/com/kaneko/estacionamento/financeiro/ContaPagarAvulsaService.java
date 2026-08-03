package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
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

/**
 * Regras de negocio da despesa avulsa a pagar, incluindo a baixa (pagamento).
 */
@Service
public class ContaPagarAvulsaService {

    private final ContaPagarAvulsaRepository repository;
    private final FornecedorRepository fornecedorRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;

    public ContaPagarAvulsaService(ContaPagarAvulsaRepository repository,
                                   FornecedorRepository fornecedorRepository,
                                   FormaPagamentoRepository formaPagamentoRepository) {
        this.repository = repository;
        this.fornecedorRepository = fornecedorRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
    }

    @Transactional(readOnly = true)
    public Page<ContaPagarAvulsa> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public ContaPagarAvulsa buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaPagarAvulsa", id));
    }

    @Transactional
    public ContaPagarAvulsa criar(ContaPagarAvulsaRequest req) {
        ContaPagarAvulsa conta = new ContaPagarAvulsa();
        aplicar(conta, req);
        conta.setValorPago(BigDecimal.ZERO);
        conta.setSituacao(SituacaoContaPagar.PENDENTE);
        return repository.save(conta);
    }

    @Transactional
    public ContaPagarAvulsa atualizar(Long id, ContaPagarAvulsaRequest req) {
        ContaPagarAvulsa conta = buscar(id);
        if (conta.getSituacao() != SituacaoContaPagar.PENDENTE) {
            throw new ConflitoException("Apenas despesas pendentes podem ser alteradas.");
        }
        aplicar(conta, req);
        return repository.save(conta);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
    }

    /** Registra um pagamento (baixa). Acumula o valor pago e atualiza a situacao. */
    @Transactional
    public ContaPagarAvulsa baixar(Long id, BaixaRequest req) {
        ContaPagarAvulsa conta = buscar(id);
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
        return repository.save(conta);
    }

    @Transactional
    public ContaPagarAvulsa cancelar(Long id) {
        ContaPagarAvulsa conta = buscar(id);
        if (conta.getSituacao() == SituacaoContaPagar.PAGA) {
            throw new ConflitoException("Uma despesa paga não pode ser cancelada.");
        }
        conta.setSituacao(SituacaoContaPagar.CANCELADA);
        return repository.save(conta);
    }

    private void aplicar(ContaPagarAvulsa conta, ContaPagarAvulsaRequest req) {
        conta.setDescricao(req.descricao().trim());
        conta.setFornecedor(req.fornecedorId() != null ? buscarFornecedor(req.fornecedorId()) : null);
        conta.setFormaPagamento(req.formaPagamentoId() != null ? buscarForma(req.formaPagamentoId()) : null);
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

    private Fornecedor buscarFornecedor(Long id) {
        return fornecedorRepository.findByIdAndOrganizacaoIdIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", id));
    }

    private FormaPagamento buscarForma(Long id) {
        return formaPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("FormaPagamento", id));
    }
}
