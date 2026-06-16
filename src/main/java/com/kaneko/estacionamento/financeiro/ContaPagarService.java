package com.kaneko.estacionamento.financeiro;

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

    public ContaPagarService(ContaPagarRepository repository,
                             FornecedorRepository fornecedorRepository,
                             CondicaoPagamentoRepository condicaoPagamentoRepository,
                             FormaPagamentoRepository formaPagamentoRepository) {
        this.repository = repository;
        this.fornecedorRepository = fornecedorRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
    }

    @Transactional(readOnly = true)
    public Page<ContaPagar> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public ContaPagar buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaPagar", id));
    }

    @Transactional
    public ContaPagar criar(ContaPagarRequest req) {
        ContaPagar conta = new ContaPagar();
        Fornecedor fornecedor = fornecedorRepository.findById(req.fornecedorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", req.fornecedorId()));
        conta.setFornecedor(fornecedor);
        if (req.condicaoPagamentoId() != null) {
            conta.setCondicaoPagamento(buscarCondicao(req.condicaoPagamentoId()));
        }
        if (req.formaPagamentoId() != null) {
            conta.setFormaPagamento(buscarForma(req.formaPagamentoId()));
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
        conta.setValorTotal(req.valorTotal() != null
                ? req.valorTotal()
                : original.subtract(desconto).add(juros).add(multa));
        conta.setValorPago(BigDecimal.ZERO);
        LocalDate emissao = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        conta.setDataEmissao(emissao);
        if (req.dataVencimento().isBefore(emissao)) {
            throw new ConflitoException("A data de vencimento não pode ser anterior à data de emissão.");
        }
        conta.setDataVencimento(req.dataVencimento());
        conta.setSituacao(SituacaoContaPagar.PENDENTE);
        conta.setObservacao(req.observacao());
        return repository.save(conta);
    }

    /** Registra um pagamento (baixa). Acumula o valor pago e atualiza a situacao. */
    @Transactional
    public ContaPagar baixar(Long id, BaixaRequest req) {
        ContaPagar conta = buscar(id);
        if (conta.getSituacao() == SituacaoContaPagar.PAGA
                || conta.getSituacao() == SituacaoContaPagar.CANCELADA) {
            throw new ConflitoException("Este título não pode receber baixa (já está pago ou cancelado).");
        }
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
        return repository.save(conta);
    }

    @Transactional
    public ContaPagar cancelar(Long id) {
        ContaPagar conta = buscar(id);
        if (conta.getSituacao() == SituacaoContaPagar.PAGA) {
            throw new ConflitoException("Um título pago não pode ser cancelado.");
        }
        conta.setSituacao(SituacaoContaPagar.CANCELADA);
        return repository.save(conta);
    }

    private CondicaoPagamento buscarCondicao(Long id) {
        return condicaoPagamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private FormaPagamento buscarForma(Long id) {
        return formaPagamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("FormaPagamento", id));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
