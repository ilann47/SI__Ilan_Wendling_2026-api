package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.compras.ItemRecebimentoCompra;
import com.kaneko.estacionamento.compras.RecebimentoCompra;
import com.kaneko.estacionamento.compras.RecebimentoCompraRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.financeiro.ContaPagar;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaPagar;
import com.kaneko.estacionamento.estoque.EstoqueService;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final LocalEstoqueRepository localEstoqueRepository;
    private final EstoqueService estoqueService;
    private final RecebimentoCompraRepository recebimentoCompraRepository;
    private final AuditoriaService auditoria;

    public NotaEntradaService(NotaEntradaRepository repository,
                              FornecedorRepository fornecedorRepository,
                              CondicaoPagamentoRepository condicaoPagamentoRepository,
                              ProdutoRepository produtoRepository,
                              ContaPagarRepository contaPagarRepository,
                              LocalEstoqueRepository localEstoqueRepository,
                              EstoqueService estoqueService,
                              RecebimentoCompraRepository recebimentoCompraRepository,
                              AuditoriaService auditoria) {
        this.repository = repository;
        this.fornecedorRepository = fornecedorRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.produtoRepository = produtoRepository;
        this.contaPagarRepository = contaPagarRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.estoqueService = estoqueService;
        this.recebimentoCompraRepository = recebimentoCompraRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<NotaEntrada> listar(Long organizacaoId, Pageable pageable) {
        return organizacaoId == null ? repository.findAllByOrganizacaoIdIsNull(pageable)
                : repository.findAllByOrganizacaoId(organizacaoId, pageable);
    }

    @Transactional(readOnly = true)
    public NotaEntrada buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaEntrada", id));
    }

    @Transactional
    public NotaEntrada criar(Long organizacaoId, NotaEntradaRequest req) {
        Fornecedor fornecedor = (organizacaoId == null
                ? fornecedorRepository.findByIdAndOrganizacaoIdIsNull(req.fornecedorId())
                : fornecedorRepository.findByIdAndOrganizacaoId(req.fornecedorId(), organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", req.fornecedorId()));
        if (Boolean.FALSE.equals(fornecedor.getAtivo())) {
            throw new ConflitoException("O fornecedor está inativo e não pode receber notas.");
        }

        NotaEntrada nota = new NotaEntrada();
        nota.setOrganizacaoId(organizacaoId);
        nota.setNumero(req.numero().trim());
        nota.setModelo(req.modelo() != null && !req.modelo().isBlank() ? req.modelo() : "55");
        nota.setSerie(req.serie() != null && !req.serie().isBlank() ? req.serie() : "1");
        nota.setFornecedor(fornecedor);
        nota.setCondicaoPagamento(req.condicaoPagamentoId() != null
                ? buscarCondicao(organizacaoId, req.condicaoPagamentoId()) : null);
        if (organizacaoId != null) {
            if (req.localEstoqueId() == null) {
                throw new IllegalArgumentException("O local de estoque e obrigatorio.");
            }
            LocalEstoque local = localEstoqueRepository
                    .findByIdAndOrganizacaoId(req.localEstoqueId(), organizacaoId)
                    .filter(l -> Boolean.TRUE.equals(l.getAtivo()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "LocalEstoque", req.localEstoqueId()));
            nota.setLocalEstoque(local);
        }
        if (req.recebimentoCompraId() != null) {
            if (organizacaoId == null) {
                throw new IllegalArgumentException(
                        "Recebimento de compra exige contexto de organizacao.");
            }
            RecebimentoCompra recebimento = recebimentoCompraRepository
                    .findByIdAndOrganizacaoId(req.recebimentoCompraId(), organizacaoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "RecebimentoCompra", req.recebimentoCompraId()));
            if (!recebimento.getOrdemCompra().getFornecedor().getId().equals(fornecedor.getId())
                    || !recebimento.getLocalEstoque().getId().equals(nota.getLocalEstoque().getId())) {
                throw new ConflitoException(
                        "Fornecedor e local da nota devem corresponder ao recebimento.");
            }
            if (repository.existsByOrganizacaoIdAndRecebimentoCompraId(
                    organizacaoId, recebimento.getId())) {
                throw new ConflitoException("O recebimento ja possui uma nota de entrada.");
            }
            nota.setRecebimentoCompra(recebimento);
        }

        boolean duplicada = organizacaoId == null
                ? repository.existsByOrganizacaoIdIsNullAndNumeroAndSerieAndFornecedorId(
                        nota.getNumero(), nota.getSerie(), fornecedor.getId())
                : repository.existsByOrganizacaoIdAndNumeroAndSerieAndFornecedorId(
                        organizacaoId, nota.getNumero(), nota.getSerie(), fornecedor.getId());
        if (duplicada) {
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
            Produto produto = (organizacaoId == null
                    ? produtoRepository.findByIdAndOrganizacaoIdIsNull(ir.produtoId())
                    : produtoRepository.findByIdAndOrganizacaoId(ir.produtoId(), organizacaoId))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", ir.produtoId()));
            ItemNotaEntrada item = new ItemNotaEntrada();
            item.setOrganizacaoId(organizacaoId);
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

        if (nota.getRecebimentoCompra() != null) {
            validarItensRecebimento(nota, nota.getRecebimentoCompra());
        }

        ratearDespesas(nota.getItens(), valorProdutos, frete, seguro, outras);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_ENTRADA_CRIADA", null, snapshot(nota));
        return nota;
    }

    /**
     * Confirma a nota: efetiva o estoque (soma a quantidade comprada e atualiza o
     * custo de cada produto) e gera os titulos a pagar.
     */
    @Transactional
    public NotaEntrada confirmar(Long organizacaoId, Long atorId, Long id) {
        NotaEntrada nota = organizacaoId == null ? buscar(null, id)
                : repository.findParaAtualizar(id, organizacaoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("NotaEntrada", id));
        if (nota.getSituacao() != SituacaoNota.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser confirmadas.");
        }
        for (ItemNotaEntrada item : nota.getItens()) {
            Produto produto = item.getProduto();
            if (organizacaoId == null) {
                produto.setQuantidade(produto.getQuantidade().add(item.getQuantidade()));
            }
            produto.setValorCompra(item.getValorUnitario());
            if (item.getCustoPrecoFinal().signum() > 0) {
                produto.setCusto(item.getCustoPrecoFinal());
            }
            produto.recalcularLucro();
            produtoRepository.saveAndFlush(produto);
            if (organizacaoId != null && nota.getRecebimentoCompra() == null) {
                estoqueService.registrarRecebimento(organizacaoId, atorId, produto.getId(),
                        nota.getLocalEstoque().getId(), item.getQuantidade(),
                        item.getCustoPrecoFinal(), "NOTA_ENTRADA",
                        nota.getId() + ":" + item.getSequencia(),
                        "Confirmacao da nota de entrada " + nota.getNumero());
            }
        }
        gerarTitulos(nota);
        Map<String, Object> anterior = snapshot(nota);
        nota.setSituacao(SituacaoNota.CONFIRMADA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_ENTRADA_CONFIRMADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public NotaEntrada cancelar(Long organizacaoId, Long id) {
        NotaEntrada nota = buscar(organizacaoId, id);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser cancelada.");
        }
        Map<String, Object> anterior = snapshot(nota);
        nota.setSituacao(SituacaoNota.CANCELADA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_ENTRADA_CANCELADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        NotaEntrada nota = buscar(organizacaoId, id);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser excluída.");
        }
        Map<String, Object> anterior = snapshot(nota);
        repository.delete(nota);
        repository.flush();
        auditar(nota, "NOTA_ENTRADA_EXCLUIDA", anterior, null);
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
                    .findByIdAndOrganizacaoId(nota.getCondicaoPagamento().getId(), nota.getOrganizacaoId())
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
        titulo.setOrganizacaoId(nota.getOrganizacaoId());
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

    private CondicaoPagamento buscarCondicao(Long organizacaoId, Long id) {
        return (organizacaoId == null
                ? condicaoPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                : condicaoPagamentoRepository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private static void validarItensRecebimento(
            NotaEntrada nota, RecebimentoCompra recebimento) {
        Map<Long, BigDecimal> quantidadesRecebidas = new HashMap<>();
        for (ItemRecebimentoCompra item : recebimento.getItens()) {
            quantidadesRecebidas.merge(item.getProduto().getId(), item.getQuantidade(),
                    BigDecimal::add);
        }
        Map<Long, BigDecimal> quantidadesNota = new HashMap<>();
        for (ItemNotaEntrada item : nota.getItens()) {
            quantidadesNota.merge(item.getProduto().getId(), item.getQuantidade(),
                    BigDecimal::add);
        }
        if (quantidadesRecebidas.size() != quantidadesNota.size()) {
            throw new ConflitoException(
                    "Os itens da nota devem corresponder integralmente ao recebimento.");
        }
        for (Map.Entry<Long, BigDecimal> item : quantidadesRecebidas.entrySet()) {
            BigDecimal quantidadeNota = quantidadesNota.get(item.getKey());
            if (quantidadeNota == null || quantidadeNota.compareTo(item.getValue()) != 0) {
                throw new ConflitoException(
                        "Os itens da nota devem corresponder integralmente ao recebimento.");
            }
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void auditar(NotaEntrada nota, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (nota.getOrganizacaoId() == null) {
            return;
        }
        auditoria.registrar(new RegistrarAuditoriaCommand(nota.getOrganizacaoId(), acao,
                "NOTA_ENTRADA", nota.getId(), null, null, null, anterior, posterior,
                null, nota.getVersion(), ClasseRetencaoAuditoria.FISCAL));
    }

    private static Map<String, Object> snapshot(NotaEntrada nota) {
        return Map.of("situacao", nota.getSituacao().name(),
                "fornecedorId", nota.getFornecedor().getId(),
                "valorTotal", nota.getValorTotal(),
                "localEstoqueId", nota.getLocalEstoque() == null
                        ? 0L : nota.getLocalEstoque().getId(),
                "recebimentoCompraId", nota.getRecebimentoCompra() == null
                        ? 0L : nota.getRecebimentoCompra().getId());
    }
}
