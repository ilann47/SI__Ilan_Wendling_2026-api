package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.financeiro.ContaPagar;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaPagar;
import com.kaneko.estacionamento.fiscal.dto.ItemNotaEntradaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaRequest;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio da nota fiscal de entrada (compra). Coordena o calculo dos
 * totais e rateios na criacao e, na confirmacao, os efeitos colaterais: soma do
 * estoque dos produtos e geracao dos titulos a pagar conforme a condicao de
 * pagamento.
 */
@Service
public class NotaEntradaService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final NotaEntradaRepository repository;
    private final FornecedorRepository fornecedorRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final ProdutoRepository produtoRepository;
    private final ContaPagarRepository contaPagarRepository;

    public NotaEntradaService(NotaEntradaRepository repository,
                              FornecedorRepository fornecedorRepository,
                              CondicaoPagamentoRepository condicaoPagamentoRepository,
                              ProdutoRepository produtoRepository,
                              ContaPagarRepository contaPagarRepository) {
        this.repository = repository;
        this.fornecedorRepository = fornecedorRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.produtoRepository = produtoRepository;
        this.contaPagarRepository = contaPagarRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotaEntrada> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public NotaEntrada buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaEntrada", id));
    }

    @Transactional
    public NotaEntrada criar(NotaEntradaRequest req) {
        Fornecedor fornecedor = fornecedorRepository.findById(req.fornecedorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", req.fornecedorId()));
        if (Boolean.FALSE.equals(fornecedor.getAtivo())) {
            throw new ConflitoException("O fornecedor está inativo e não pode receber notas.");
        }

        NotaEntrada nota = new NotaEntrada();
        nota.setNumero(req.numero().trim());
        nota.setModelo(req.modelo() != null && !req.modelo().isBlank() ? req.modelo() : "55");
        nota.setSerie(req.serie() != null && !req.serie().isBlank() ? req.serie() : "1");
        nota.setFornecedor(fornecedor);
        nota.setCondicaoPagamento(req.condicaoPagamentoId() != null
                ? buscarCondicao(req.condicaoPagamentoId()) : null);

        if (repository.existsByNumeroAndSerieAndFornecedorId(nota.getNumero(), nota.getSerie(), fornecedor.getId())) {
            throw new ConflitoException("Já existe uma nota com este número/série para este fornecedor.");
        }

        LocalDate emissao = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        nota.setDataEmissao(emissao);
        if (req.dataChegada() != null && req.dataChegada().isBefore(emissao)) {
            throw new ConflitoException("A data de chegada não pode ser anterior à emissão.");
        }
        nota.setDataChegada(req.dataChegada());
        nota.setTipoFrete(req.tipoFrete() != null ? req.tipoFrete() : TipoFrete.CIF);

        BigDecimal frete = nz(req.valorFrete());
        BigDecimal seguro = nz(req.valorSeguro());
        BigDecimal outras = nz(req.outrasDespesas());
        BigDecimal descontoNota = nz(req.valorDesconto());
        nota.setValorFrete(frete);
        nota.setValorSeguro(seguro);
        nota.setOutrasDespesas(outras);
        nota.setValorDesconto(descontoNota);
        nota.setObservacao(req.observacao());
        nota.setSituacao(SituacaoNota.PENDENTE);

        BigDecimal valorProdutos = BigDecimal.ZERO;
        int seq = 1;
        for (ItemNotaEntradaRequest ir : req.itens()) {
            Produto produto = produtoRepository.findById(ir.produtoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", ir.produtoId()));
            ItemNotaEntrada item = new ItemNotaEntrada();
            item.setProduto(produto);
            item.setSequencia(seq++);
            item.setQuantidade(ir.quantidade());
            item.setValorUnitario(ir.valorUnitario());
            BigDecimal descItem = nz(ir.valorDesconto());
            item.setValorDesconto(descItem);
            item.setPercentualDesconto(nz(ir.percentualDesconto()));
            BigDecimal totalItem = ir.quantidade().multiply(ir.valorUnitario())
                    .subtract(descItem).setScale(2, RoundingMode.HALF_UP);
            if (totalItem.signum() < 0) {
                throw new ConflitoException("O desconto do item não pode exceder o seu valor.");
            }
            item.setValorTotal(totalItem);
            nota.addItem(item);
            valorProdutos = valorProdutos.add(totalItem);
        }
        nota.setValorProdutos(valorProdutos);

        BigDecimal total = valorProdutos.add(frete).add(seguro).add(outras)
                .subtract(descontoNota).setScale(2, RoundingMode.HALF_UP);
        if (total.signum() < 0) {
            throw new ConflitoException("O desconto da nota não pode exceder o seu valor.");
        }
        nota.setValorTotal(total);

        ratearDespesas(nota.getItens(), valorProdutos, frete, seguro, outras);
        return repository.save(nota);
    }

    /**
     * Confirma a nota: efetiva o estoque (soma a quantidade comprada e atualiza o
     * custo de cada produto) e gera os titulos a pagar.
     */
    @Transactional
    public NotaEntrada confirmar(Long id) {
        NotaEntrada nota = buscar(id);
        if (nota.getSituacao() != SituacaoNota.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser confirmadas.");
        }
        for (ItemNotaEntrada item : nota.getItens()) {
            Produto produto = item.getProduto();
            produto.setQuantidade(produto.getQuantidade().add(item.getQuantidade()));
            produto.setValorCompra(item.getValorUnitario());
            if (item.getCustoPrecoFinal().signum() > 0) {
                produto.setCusto(item.getCustoPrecoFinal());
            }
            produto.recalcularLucro();
            produtoRepository.save(produto);
        }
        gerarTitulos(nota);
        nota.setSituacao(SituacaoNota.CONFIRMADA);
        return repository.save(nota);
    }

    @Transactional
    public NotaEntrada cancelar(Long id) {
        NotaEntrada nota = buscar(id);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser cancelada.");
        }
        nota.setSituacao(SituacaoNota.CANCELADA);
        return repository.save(nota);
    }

    @Transactional
    public void excluir(Long id) {
        NotaEntrada nota = buscar(id);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser excluída.");
        }
        repository.delete(nota);
    }

    /** Distribui frete/seguro/outras despesas proporcionalmente ao valor de cada item. */
    private void ratearDespesas(List<ItemNotaEntrada> itens, BigDecimal base,
                                BigDecimal frete, BigDecimal seguro, BigDecimal outras) {
        for (ItemNotaEntrada item : itens) {
            BigDecimal proporcao = base.signum() == 0
                    ? BigDecimal.ZERO
                    : item.getValorTotal().divide(base, 8, RoundingMode.HALF_UP);
            BigDecimal rFrete = frete.multiply(proporcao).setScale(2, RoundingMode.HALF_UP);
            BigDecimal rSeguro = seguro.multiply(proporcao).setScale(2, RoundingMode.HALF_UP);
            BigDecimal rOutras = outras.multiply(proporcao).setScale(2, RoundingMode.HALF_UP);
            item.setRateioFrete(rFrete);
            item.setRateioSeguro(rSeguro);
            item.setRateioOutras(rOutras);
            BigDecimal custoTotal = item.getValorTotal().add(rFrete).add(rSeguro).add(rOutras);
            BigDecimal custoUnitario = item.getQuantidade().signum() == 0
                    ? BigDecimal.ZERO
                    : custoTotal.divide(item.getQuantidade(), 2, RoundingMode.HALF_UP);
            item.setCustoPrecoFinal(custoUnitario);
        }
    }

    /** Gera os titulos a pagar conforme as parcelas da condicao (ou um titulo a vista). */
    private void gerarTitulos(NotaEntrada nota) {
        List<ParcelaCondicaoPagamento> parcelas = List.of();
        if (nota.getCondicaoPagamento() != null) {
            CondicaoPagamento condicao = condicaoPagamentoRepository
                    .findById(nota.getCondicaoPagamento().getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "CondicaoPagamento", nota.getCondicaoPagamento().getId()));
            parcelas = condicao.getParcelas().stream()
                    .sorted(Comparator.comparing(ParcelaCondicaoPagamento::getNumero))
                    .toList();
        }

        BigDecimal total = nota.getValorTotal();
        if (parcelas.isEmpty()) {
            salvarTitulo(nota, 1, 1, total, nota.getDataEmissao(), null);
            return;
        }

        int n = parcelas.size();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            ParcelaCondicaoPagamento parcela = parcelas.get(i);
            BigDecimal valorParcela;
            if (i == n - 1) {
                valorParcela = total.subtract(acumulado);
            } else {
                valorParcela = total.multiply(parcela.getPercentual())
                        .divide(CEM, 2, RoundingMode.HALF_UP);
                acumulado = acumulado.add(valorParcela);
            }
            LocalDate vencimento = nota.getDataEmissao().plusDays(parcela.getDias());
            ContaPagar titulo = salvarTitulo(nota, i + 1, n, valorParcela, vencimento,
                    parcela.getFormaPagamento());
            titulo.setCondicaoPagamento(nota.getCondicaoPagamento());
        }
    }

    private ContaPagar salvarTitulo(NotaEntrada nota, int numeroParcela, int totalParcelas,
                                    BigDecimal valor, LocalDate vencimento,
                                    com.kaneko.estacionamento.pagamento.FormaPagamento forma) {
        ContaPagar titulo = new ContaPagar();
        titulo.setFornecedor(nota.getFornecedor());
        titulo.setNotaEntrada(nota);
        titulo.setFormaPagamento(forma);
        titulo.setNumeroParcela(numeroParcela);
        titulo.setTotalParcelas(totalParcelas);
        titulo.setValorOriginal(valor);
        titulo.setValorTotal(valor);
        titulo.setValorPago(BigDecimal.ZERO);
        titulo.setDataEmissao(nota.getDataEmissao());
        titulo.setDataVencimento(vencimento);
        titulo.setSituacao(SituacaoContaPagar.PENDENTE);
        return contaPagarRepository.save(titulo);
    }

    private CondicaoPagamento buscarCondicao(Long id) {
        return condicaoPagamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
