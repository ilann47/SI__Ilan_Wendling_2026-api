package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.pagamento.dto.CondicaoPagamentoRequest;
import com.kaneko.estacionamento.pagamento.dto.ParcelaRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio da condicao de pagamento (agregado com parcelas).
 *
 * <p>Garante a unicidade do nome e, quando o detalhamento de parcelas e
 * informado, valida que a soma dos percentuais e 100 e que a quantidade de
 * parcelas confere com {@code numeroParcelas}. As formas de pagamento
 * referenciadas pelas parcelas sao validadas. A sincronizacao das parcelas e
 * feita mutando a colecao (clear/add) para preservar o {@code orphanRemoval}.
 */
@Service
public class CondicaoPagamentoService {

    private static final BigDecimal CEM = new BigDecimal("100.00");

    private final CondicaoPagamentoRepository repository;
    private final FormaPagamentoRepository formaPagamentoRepository;

    public CondicaoPagamentoService(CondicaoPagamentoRepository repository,
                                    FormaPagamentoRepository formaPagamentoRepository) {
        this.repository = repository;
        this.formaPagamentoRepository = formaPagamentoRepository;
    }

    @Transactional(readOnly = true)
    public Page<CondicaoPagamento> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public CondicaoPagamento buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    @Transactional
    public CondicaoPagamento criar(CondicaoPagamentoRequest req) {
        String nome = req.nome().trim();
        if (repository.existsByNome(nome)) {
            throw new ConflitoException("Ja existe uma condicao de pagamento com este nome.");
        }
        validarParcelas(req);
        CondicaoPagamento condicao = new CondicaoPagamento();
        aplicar(condicao, req, nome);
        sincronizarParcelas(condicao, req.parcelas());
        return repository.save(condicao);
    }

    @Transactional
    public CondicaoPagamento atualizar(Long id, CondicaoPagamentoRequest req) {
        CondicaoPagamento condicao = buscar(id);
        String nome = req.nome().trim();
        if (repository.existsByNomeAndIdNot(nome, id)) {
            throw new ConflitoException("Ja existe uma condicao de pagamento com este nome.");
        }
        validarParcelas(req);
        aplicar(condicao, req, nome);
        sincronizarParcelas(condicao, req.parcelas());
        return repository.save(condicao);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
    }

    private void aplicar(CondicaoPagamento condicao, CondicaoPagamentoRequest req, String nome) {
        condicao.setNome(nome);
        condicao.setNumeroParcelas(req.numeroParcelas());
        condicao.setDiasPrimeiraParcela(req.diasPrimeiraParcela() != null ? req.diasPrimeiraParcela() : 0);
        condicao.setDiasEntreParcelas(req.diasEntreParcelas() != null ? req.diasEntreParcelas() : 0);
        condicao.setPercentualJuros(req.percentualJuros() != null ? req.percentualJuros() : BigDecimal.ZERO);
        condicao.setPercentualMulta(req.percentualMulta() != null ? req.percentualMulta() : BigDecimal.ZERO);
        condicao.setPercentualDesconto(req.percentualDesconto() != null ? req.percentualDesconto() : BigDecimal.ZERO);
        if (req.ativo() != null) {
            condicao.setAtivo(req.ativo());
        }
    }

    /** Valida o detalhamento de parcelas, quando informado (soma 100% e quantidade coerente). */
    private void validarParcelas(CondicaoPagamentoRequest req) {
        List<ParcelaRequest> parcelas = req.parcelas();
        if (parcelas == null || parcelas.isEmpty()) {
            return;
        }
        if (parcelas.size() != req.numeroParcelas()) {
            throw new ConflitoException("A quantidade de parcelas detalhadas deve ser igual ao número de parcelas.");
        }
        BigDecimal soma = parcelas.stream()
                .map(ParcelaRequest::percentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.compareTo(CEM) != 0) {
            throw new ConflitoException("A soma dos percentuais das parcelas deve ser 100.");
        }
    }

    /** Recria as parcelas mutando a colecao do agregado (preserva orphanRemoval). */
    private void sincronizarParcelas(CondicaoPagamento condicao, List<ParcelaRequest> parcelas) {
        condicao.getParcelas().clear();
        if (parcelas == null) {
            return;
        }
        for (ParcelaRequest pr : parcelas) {
            ParcelaCondicaoPagamento parcela = new ParcelaCondicaoPagamento();
            parcela.setCondicaoPagamento(condicao);
            parcela.setNumero(pr.numero());
            parcela.setDias(pr.dias());
            parcela.setPercentual(pr.percentual());
            parcela.setFormaPagamento(pr.formaPagamentoId() != null ? buscarForma(pr.formaPagamentoId()) : null);
            condicao.getParcelas().add(parcela);
        }
    }

    private FormaPagamento buscarForma(Long formaPagamentoId) {
        return formaPagamentoRepository.findById(formaPagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("FormaPagamento", formaPagamentoId));
    }
}
