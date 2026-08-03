package com.kaneko.estacionamento.financeiro;

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

    public ContaReceberService(ContaReceberRepository repository,
                               ClienteRepository clienteRepository,
                               CondicaoPagamentoRepository condicaoPagamentoRepository,
                               FormaPagamentoRepository formaPagamentoRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
    }

    @Transactional(readOnly = true)
    public Page<ContaReceber> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public ContaReceber buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ContaReceber", id));
    }

    @Transactional
    public ContaReceber criar(ContaReceberRequest req) {
        ContaReceber conta = new ContaReceber();
        Cliente cliente = clienteRepository.findById(req.clienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", req.clienteId()));
        conta.setCliente(cliente);
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
        conta.setValorRecebido(BigDecimal.ZERO);
        LocalDate emissao = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        conta.setDataEmissao(emissao);
        if (req.dataVencimento().isBefore(emissao)) {
            throw new ConflitoException("A data de vencimento não pode ser anterior à data de emissão.");
        }
        conta.setDataVencimento(req.dataVencimento());
        conta.setSituacao(SituacaoContaReceber.PENDENTE);
        conta.setObservacao(req.observacao());
        return repository.save(conta);
    }

    /** Registra um recebimento (baixa). Acumula o valor recebido e atualiza a situacao. */
    @Transactional
    public ContaReceber baixar(Long id, BaixaRequest req) {
        ContaReceber conta = buscar(id);
        if (conta.getSituacao() == SituacaoContaReceber.RECEBIDA
                || conta.getSituacao() == SituacaoContaReceber.CANCELADA) {
            throw new ConflitoException("Este título não pode receber baixa (já está recebido ou cancelado).");
        }
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
        return repository.save(conta);
    }

    @Transactional
    public ContaReceber cancelar(Long id) {
        ContaReceber conta = buscar(id);
        if (conta.getSituacao() == SituacaoContaReceber.RECEBIDA) {
            throw new ConflitoException("Um título recebido não pode ser cancelado.");
        }
        conta.setSituacao(SituacaoContaReceber.CANCELADA);
        return repository.save(conta);
    }

    private CondicaoPagamento buscarCondicao(Long id) {
        return condicaoPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private FormaPagamento buscarForma(Long id) {
        return formaPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("FormaPagamento", id));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
