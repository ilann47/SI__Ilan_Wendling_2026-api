package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.fiscal.dto.ItemNotaSaidaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaRequest;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
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
 * Regras de negocio da nota fiscal de saida (venda). Na confirmacao, valida que
 * ha estoque suficiente, baixa o estoque dos produtos e gera os titulos a
 * receber conforme a condicao de pagamento.
 */
@Service
public class NotaSaidaService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final NotaSaidaRepository repository;
    private final ClienteRepository clienteRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final ProdutoRepository produtoRepository;
    private final ContaReceberRepository contaReceberRepository;

    public NotaSaidaService(NotaSaidaRepository repository,
                            ClienteRepository clienteRepository,
                            CondicaoPagamentoRepository condicaoPagamentoRepository,
                            ProdutoRepository produtoRepository,
                            ContaReceberRepository contaReceberRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.produtoRepository = produtoRepository;
        this.contaReceberRepository = contaReceberRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotaSaida> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public NotaSaida buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaSaida", id));
    }

    @Transactional
    public NotaSaida criar(NotaSaidaRequest req) {
        Cliente cliente = clienteRepository.findById(req.clienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", req.clienteId()));

        NotaSaida nota = new NotaSaida();
        nota.setNumero(req.numero().trim());
        nota.setModelo(req.modelo() != null && !req.modelo().isBlank() ? req.modelo() : "55");
        nota.setSerie(req.serie() != null && !req.serie().isBlank() ? req.serie() : "1");
        nota.setCliente(cliente);
        nota.setCondicaoPagamento(req.condicaoPagamentoId() != null
                ? buscarCondicao(req.condicaoPagamentoId()) : null);

        if (repository.existsByNumeroAndSerieAndClienteId(nota.getNumero(), nota.getSerie(), cliente.getId())) {
            throw new ConflitoException("Já existe uma nota com este número/série para este cliente.");
        }

        LocalDate emissao = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        nota.setDataEmissao(emissao);
        if (req.dataSaida() != null && req.dataSaida().isBefore(emissao)) {
            throw new ConflitoException("A data de saída não pode ser anterior à emissão.");
        }
        nota.setDataSaida(req.dataSaida());
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
        for (ItemNotaSaidaRequest ir : req.itens()) {
            Produto produto = produtoRepository.findById(ir.produtoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", ir.produtoId()));
            ItemNotaSaida item = new ItemNotaSaida();
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
            item.setCustoPrecoFinal(produto.getValorCompra());
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
     * Confirma a nota: valida o estoque disponivel, baixa a quantidade vendida e
     * gera os titulos a receber.
     */
    @Transactional
    public NotaSaida confirmar(Long id) {
        NotaSaida nota = buscar(id);
        if (nota.getSituacao() != SituacaoNota.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser confirmadas.");
        }
        for (ItemNotaSaida item : nota.getItens()) {
            Produto produto = item.getProduto();
            if (produto.getQuantidade().compareTo(item.getQuantidade()) < 0) {
                throw new ConflitoException("Estoque insuficiente para o produto '" + produto.getNome()
                        + "' (disponível: " + produto.getQuantidade().stripTrailingZeros().toPlainString() + ").");
            }
        }
        for (ItemNotaSaida item : nota.getItens()) {
            Produto produto = item.getProduto();
            produto.setQuantidade(produto.getQuantidade().subtract(item.getQuantidade()));
            produtoRepository.save(produto);
        }
        gerarTitulos(nota);
        nota.setSituacao(SituacaoNota.CONFIRMADA);
        return repository.save(nota);
    }

    @Transactional
    public NotaSaida cancelar(Long id) {
        NotaSaida nota = buscar(id);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser cancelada.");
        }
        nota.setSituacao(SituacaoNota.CANCELADA);
        return repository.save(nota);
    }

    @Transactional
    public void excluir(Long id) {
        NotaSaida nota = buscar(id);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser excluída.");
        }
        repository.delete(nota);
    }

    /** Distribui frete/seguro/outras despesas proporcionalmente ao valor de cada item. */
    private void ratearDespesas(List<ItemNotaSaida> itens, BigDecimal base,
                                BigDecimal frete, BigDecimal seguro, BigDecimal outras) {
        for (ItemNotaSaida item : itens) {
            BigDecimal proporcao = base.signum() == 0
                    ? BigDecimal.ZERO
                    : item.getValorTotal().divide(base, 8, RoundingMode.HALF_UP);
            item.setRateioFrete(frete.multiply(proporcao).setScale(2, RoundingMode.HALF_UP));
            item.setRateioSeguro(seguro.multiply(proporcao).setScale(2, RoundingMode.HALF_UP));
            item.setRateioOutras(outras.multiply(proporcao).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /** Gera os titulos a receber conforme as parcelas da condicao (ou um titulo a vista). */
    private void gerarTitulos(NotaSaida nota) {
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
            ContaReceber titulo = salvarTitulo(nota, i + 1, n, valorParcela, vencimento,
                    parcela.getFormaPagamento());
            titulo.setCondicaoPagamento(nota.getCondicaoPagamento());
        }
    }

    private ContaReceber salvarTitulo(NotaSaida nota, int numeroParcela, int totalParcelas,
                                      BigDecimal valor, LocalDate vencimento,
                                      com.kaneko.estacionamento.pagamento.FormaPagamento forma) {
        ContaReceber titulo = new ContaReceber();
        titulo.setCliente(nota.getCliente());
        titulo.setNotaSaida(nota);
        titulo.setFormaPagamento(forma);
        titulo.setNumeroParcela(numeroParcela);
        titulo.setTotalParcelas(totalParcelas);
        titulo.setValorOriginal(valor);
        titulo.setValorTotal(valor);
        titulo.setValorRecebido(BigDecimal.ZERO);
        titulo.setDataEmissao(nota.getDataEmissao());
        titulo.setDataVencimento(vencimento);
        titulo.setSituacao(SituacaoContaReceber.PENDENTE);
        return contaReceberRepository.save(titulo);
    }

    private CondicaoPagamento buscarCondicao(Long id) {
        return condicaoPagamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
