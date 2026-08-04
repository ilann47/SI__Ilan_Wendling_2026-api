package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.estoque.EstoqueService;
import com.kaneko.estacionamento.estoque.LocalEstoque;
import com.kaneko.estacionamento.estoque.LocalEstoqueRepository;
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
import java.util.Map;
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
    private final LocalEstoqueRepository localEstoqueRepository;
    private final EstoqueService estoqueService;
    private final AuditoriaService auditoria;

    public NotaSaidaService(NotaSaidaRepository repository,
                            ClienteRepository clienteRepository,
                            CondicaoPagamentoRepository condicaoPagamentoRepository,
                            ProdutoRepository produtoRepository,
                            ContaReceberRepository contaReceberRepository,
                            LocalEstoqueRepository localEstoqueRepository,
                            EstoqueService estoqueService,
                            AuditoriaService auditoria) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.produtoRepository = produtoRepository;
        this.contaReceberRepository = contaReceberRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.estoqueService = estoqueService;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<NotaSaida> listar(Long organizacaoId, Pageable pageable) {
        return organizacaoId == null ? repository.findAllByOrganizacaoIdIsNull(pageable)
                : repository.findAllByOrganizacaoId(organizacaoId, pageable);
    }

    @Transactional(readOnly = true)
    public NotaSaida buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaSaida", id));
    }

    @Transactional
    public NotaSaida criar(Long organizacaoId, NotaSaidaRequest req) {
        Cliente cliente = (organizacaoId == null
                ? clienteRepository.findByIdAndOrganizacaoIdIsNull(req.clienteId())
                : clienteRepository.findByIdAndOrganizacaoId(req.clienteId(), organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", req.clienteId()));
        if (Boolean.FALSE.equals(cliente.getAtivo())) {
            throw new ConflitoException("O cliente está inativo e não pode receber notas.");
        }

        NotaSaida nota = new NotaSaida();
        nota.setOrganizacaoId(organizacaoId);
        nota.setNumero(req.numero().trim());
        nota.setModelo(req.modelo() != null && !req.modelo().isBlank() ? req.modelo() : "55");
        nota.setSerie(req.serie() != null && !req.serie().isBlank() ? req.serie() : "1");
        nota.setCliente(cliente);
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

        boolean duplicada = organizacaoId == null
                ? repository.existsByOrganizacaoIdIsNullAndNumeroAndSerieAndClienteId(
                        nota.getNumero(), nota.getSerie(), cliente.getId())
                : repository.existsByOrganizacaoIdAndNumeroAndSerieAndClienteId(
                        organizacaoId, nota.getNumero(), nota.getSerie(), cliente.getId());
        if (duplicada) {
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
            Produto produto = (organizacaoId == null
                    ? produtoRepository.findByIdAndOrganizacaoIdIsNull(ir.produtoId())
                    : produtoRepository.findByIdAndOrganizacaoId(ir.produtoId(), organizacaoId))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", ir.produtoId()));
            ItemNotaSaida item = new ItemNotaSaida();
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
            item.setCustoPrecoFinal(produto.getCusto());
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
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SAIDA_CRIADA", null, snapshot(nota));
        return nota;
    }

    /**
     * Confirma a nota: valida o estoque disponivel, baixa a quantidade vendida e
     * gera os titulos a receber.
     */
    @Transactional
    public NotaSaida confirmar(Long organizacaoId, Long atorId, Long id) {
        NotaSaida nota = organizacaoId == null ? buscar(null, id)
                : repository.findParaAtualizar(id, organizacaoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("NotaSaida", id));
        if (nota.getSituacao() != SituacaoNota.PENDENTE) {
            throw new ConflitoException("Apenas notas pendentes podem ser confirmadas.");
        }
        if (organizacaoId == null) {
            for (ItemNotaSaida item : nota.getItens()) {
                Produto produto = item.getProduto();
                if (produto.getQuantidade().compareTo(item.getQuantidade()) < 0) {
                    throw new ConflitoException("Estoque insuficiente para o produto '" + produto.getNome()
                            + "' (disponível: " + produto.getQuantidade().stripTrailingZeros().toPlainString() + ").");
                }
            }
        }
        for (ItemNotaSaida item : nota.getItens()) {
            Produto produto = item.getProduto();
            if (organizacaoId == null) {
                produto.setQuantidade(produto.getQuantidade().subtract(item.getQuantidade()));
                produtoRepository.saveAndFlush(produto);
            } else {
                estoqueService.registrarBaixa(organizacaoId, atorId, produto.getId(),
                        nota.getLocalEstoque().getId(), item.getQuantidade(),
                        item.getCustoPrecoFinal(), "NOTA_SAIDA",
                        nota.getId() + ":" + item.getSequencia(),
                        "Confirmacao da nota de saida " + nota.getNumero());
            }
        }
        gerarTitulos(nota);
        Map<String, Object> anterior = snapshot(nota);
        nota.setSituacao(SituacaoNota.CONFIRMADA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SAIDA_CONFIRMADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public NotaSaida cancelar(Long organizacaoId, Long id) {
        NotaSaida nota = buscar(organizacaoId, id);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser cancelada.");
        }
        Map<String, Object> anterior = snapshot(nota);
        nota.setSituacao(SituacaoNota.CANCELADA);
        nota = repository.saveAndFlush(nota);
        auditar(nota, "NOTA_SAIDA_CANCELADA", anterior, snapshot(nota));
        return nota;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        NotaSaida nota = buscar(organizacaoId, id);
        if (nota.getSituacao() == SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Uma nota confirmada não pode ser excluída.");
        }
        Map<String, Object> anterior = snapshot(nota);
        repository.delete(nota);
        repository.flush();
        auditar(nota, "NOTA_SAIDA_EXCLUIDA", anterior, null);
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
            CondicaoPagamento condicao = buscarCondicao(
                    nota.getOrganizacaoId(), nota.getCondicaoPagamento().getId());
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
        titulo.setOrganizacaoId(nota.getOrganizacaoId());
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

    private CondicaoPagamento buscarCondicao(Long organizacaoId, Long id) {
        return (organizacaoId == null
                ? condicaoPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                : condicaoPagamentoRepository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void auditar(NotaSaida nota, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (nota.getOrganizacaoId() == null) return;
        auditoria.registrar(new RegistrarAuditoriaCommand(nota.getOrganizacaoId(), acao,
                "NOTA_SAIDA", nota.getId(), null, null, null, anterior, posterior,
                null, nota.getVersion(), ClasseRetencaoAuditoria.FISCAL));
    }

    private static Map<String, Object> snapshot(NotaSaida nota) {
        return Map.of("situacao", nota.getSituacao().name(),
                "clienteId", nota.getCliente().getId(), "valorTotal", nota.getValorTotal(),
                "localEstoqueId", nota.getLocalEstoque() == null
                        ? 0L : nota.getLocalEstoque().getId());
    }
}
