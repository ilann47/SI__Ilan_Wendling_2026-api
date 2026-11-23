package com.kaneko.estacionamento.estoque;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.estoque.dto.PosicaoEstoqueResponse;
import com.kaneko.estacionamento.estoque.dto.SaldoEstoqueResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstoqueService {
    private static final String RECURSO = "MOVIMENTO_ESTOQUE";

    private final ProdutoRepository produtos;
    private final LocalEstoqueRepository locais;
    private final SaldoEstoqueRepository saldos;
    private final MovimentoEstoqueRepository movimentos;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    public EstoqueService(ProdutoRepository produtos, LocalEstoqueRepository locais,
            SaldoEstoqueRepository saldos, MovimentoEstoqueRepository movimentos,
            AuditoriaService auditoria,
            OutboxService outbox, JdbcTemplate jdbc) {
        this.produtos = produtos;
        this.locais = locais;
        this.saldos = saldos;
        this.movimentos = movimentos;
        this.auditoria = auditoria;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<SaldoEstoqueResponse> listarSaldos(Long organizacaoId, Long produtoId,
            Long localId, Pageable pageable) {
        return saldos.listarSaldos(organizacaoId, produtoId, localId, pageable)
                .map(SaldoEstoqueResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PosicaoEstoqueResponse> listarPosicoes(Long organizacaoId, Long produtoId,
            Boolean abaixoMinimo, Pageable pageable) {
        return saldos.listarPosicoes(organizacaoId, produtoId, abaixoMinimo, pageable)
                .map(PosicaoEstoqueResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<MovimentoEstoqueResponse> listarMovimentos(Long organizacaoId,
            Long produtoId, Long localId, TipoMovimentoEstoque tipo,
            OffsetDateTime de, OffsetDateTime ate, Pageable pageable) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException("O instante de nao pode ser posterior ao instante ate.");
        }
        return movimentos.listar(organizacaoId, produtoId, localId,
                        tipo == null ? null : tipo.name(), de, ate, pageable)
                .map(MovimentoEstoqueResponse::from);
    }

    @Transactional(readOnly = true)
    public MovimentoEstoqueResponse buscarMovimento(Long organizacaoId, Long id) {
        return movimentos.buscarView(id, organizacaoId).map(MovimentoEstoqueResponse::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("MovimentoEstoque", id));
    }

    /** Registra o recebimento confirmado por um documento fiscal do mesmo tenant. */
    @Transactional
    public Long registrarRecebimento(Long organizacaoId, Long atorId, Long produtoId,
            Long localId, BigDecimal quantidade, BigDecimal custoUnitario,
            String origemTipo, String origemChave, String motivo) {
        return registrarRecebimentoComCustoMedio(organizacaoId, atorId, produtoId, localId,
                quantidade, custoUnitario, origemTipo, origemChave, motivo).movimentoId();
    }

    /** Registra a entrada e devolve a memoria do custo medio realmente aplicado. */
    @Transactional
    public RecebimentoEstoqueResultado registrarRecebimentoComCustoMedio(
            Long organizacaoId, Long atorId, Long produtoId, Long localId,
            BigDecimal quantidade, BigDecimal custoUnitario,
            String origemTipo, String origemChave, String motivo) {
        validarDelta(quantidade);
        if (quantidade.signum() <= 0) {
            throw new IllegalArgumentException("A quantidade recebida deve ser positiva.");
        }
        if (custoUnitario == null || custoUnitario.signum() < 0) {
            throw new IllegalArgumentException("O custo unitario recebido deve ser nao negativo.");
        }
        bloquearProduto(organizacaoId, produtoId);
        Produto produto = buscarProdutoAtivo(organizacaoId, produtoId);
        // Um documento fiscal pode já ter carregado o produto antes do lock do razão.
        entityManager.refresh(produto, LockModeType.PESSIMISTIC_WRITE);
        LocalEstoque local = buscarLocalAtivo(organizacaoId, localId);
        BigDecimal saldoTotalAnterior = saldos.somarPorProduto(organizacaoId, produtoId);
        BigDecimal custoMedioAnterior = produto.getCusto() == null
                ? BigDecimal.ZERO : produto.getCusto();
        BigDecimal custoMedioPosterior = CustoMedioPonderado.calcularEmMilionesimos(saldoTotalAnterior,
                custoMedioAnterior, quantidade, custoUnitario);
        produto.setCusto(custoMedioPosterior);
        produto.recalcularLucro();
        produtos.saveAndFlush(produto);
        MovimentoEstoque movimento = aplicarMovimento(organizacaoId, atorId, produto, local,
                TipoMovimentoEstoque.RECEBIMENTO, quantidade, custoUnitario,
                custoMedioAnterior, custoMedioPosterior,
                origemTipo, origemChave, null, motivo);
        return new RecebimentoEstoqueResultado(movimento.getId(),
                movimento.getSaldoTotalAnterior(), movimento.getSaldoTotalPosterior(),
                custoMedioAnterior, custoMedioPosterior);
    }

    /** Adquire os mesmos locks do razão em ordem determinística, até o commit do documento. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void bloquearProdutos(Long organizacaoId, java.util.Collection<Long> ids) {
        ids.stream().distinct().sorted().forEach(id -> bloquearProduto(organizacaoId, id));
    }

    /** Registra a baixa e devolve o custo médio efetivamente aplicado. */
    @Transactional
    public BigDecimal registrarBaixa(Long organizacaoId, Long atorId, Long produtoId,
            Long localId, BigDecimal quantidade,
            String origemTipo, String origemChave, String motivo) {
        validarDelta(quantidade);
        if (quantidade.signum() <= 0) {
            throw new IllegalArgumentException("A quantidade baixada deve ser positiva.");
        }
        bloquearProduto(organizacaoId, produtoId);
        Produto produto = buscarProdutoAtivo(organizacaoId, produtoId);
        LocalEstoque local = buscarLocalAtivo(organizacaoId, localId);
        // O item pode ter carregado Produto no contexto JPA antes de obter o lock.
        // Leia o custo no banco sob o lock, não a instância já gerenciada.
        BigDecimal custoUnitario = jdbc.queryForObject(
                "select coalesce(custo_micros, 0) * 0.000001 from produto where id=? and organizacao_id=? for update",
                BigDecimal.class, produtoId, organizacaoId);
        aplicarMovimento(organizacaoId, atorId, produto, local,
                TipoMovimentoEstoque.BAIXA, quantidade.negate(), custoUnitario,
                custoUnitario, custoUnitario, origemTipo, origemChave, null, motivo);
        return custoUnitario;
    }

    /** Identifica cada lançamento esperado, inclusive produtos repetidos em linhas diferentes. */
    public record ItemReversaoFiscal(int sequencia, Long produtoId, BigDecimal quantidade) {}

    /** Só pode ser chamado pelo comando fiscal já transacional e autorizado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> reverterNotaFiscal(Long org, Long ator, Long notaId, Long localId,
            boolean entrada, List<ItemReversaoFiscal> itens, String motivo) {
        String origem = entrada ? "NOTA_ENTRADA" : "NOTA_SAIDA";
        String prefixo = notaId + ":";
        Map<String, ItemReversaoFiscal> esperados = itens.stream().collect(Collectors.toMap(
                item -> prefixo + item.sequencia(), Function.identity()));
        itens.stream().map(ItemReversaoFiscal::produtoId).distinct().sorted()
                .forEach(produto -> bloquearProduto(org, produto));
        LocalEstoque local = buscarLocalAtivo(org, localId);
        var originais = movimentos.findByOrganizacaoIdAndOrigemTipoAndOrigemChaveStartingWithOrderByIdDesc(
                org, origem, prefixo);
        if (originais.isEmpty() || originais.size() != esperados.size()) {
            throw new ConflitoException("Os movimentos originais não conciliam com todos os itens da nota.");
        }
        Map<Long, List<MovimentoEstoque>> porProduto = new TreeMap<>();
        for (MovimentoEstoque original : originais) {
            ItemReversaoFiscal item = esperados.get(original.getOrigemChave());
            if (item == null || !item.produtoId().equals(original.getProdutoId())
                    || !localId.equals(original.getLocalEstoqueId())
                    || original.getTipo() != (entrada ? TipoMovimentoEstoque.RECEBIMENTO : TipoMovimentoEstoque.BAIXA)
                    || original.getDelta().compareTo(entrada ? item.quantidade() : item.quantidade().negate()) != 0
                    || original.getCompensadoId() != null
                    || original.getCustoMedioAnterior() == null || original.getCustoMedioPosterior() == null
                    || original.getSaldoTotalAnterior() == null || original.getSaldoTotalPosterior() == null) {
                throw new ConflitoException("O movimento original não tem dados suficientes para reversão segura.");
            }
            if (movimentos.existsByCompensadoId(original.getId())) {
                throw new ConflitoException("Um dos movimentos da nota já foi compensado.");
            }
            porProduto.computeIfAbsent(original.getProdutoId(), ignored -> new ArrayList<>()).add(original);
        }
        List<Long> ids = new ArrayList<>();
        for (var grupo : porProduto.entrySet()) {
            var movimentosProduto = grupo.getValue();
            Long primeiro = movimentosProduto.getLast().getId();
            if (movimentos.countByOrganizacaoIdAndProdutoIdAndIdGreaterThan(org, grupo.getKey(), primeiro)
                    != movimentosProduto.size() - 1L) {
                throw new ConflitoException("Há movimentos posteriores ou intercalados para um produto da nota.");
            }
            Produto produto = buscarProdutoAtivo(org, grupo.getKey());
            // Nota/itens podem ter carregado Produto antes do lock do razão.
            entityManager.refresh(produto, LockModeType.PESSIMISTIC_WRITE);
            for (MovimentoEstoque original : movimentosProduto) {
                BigDecimal custoAntes = produto.getCusto() == null ? BigDecimal.ZERO : produto.getCusto();
                if (custoAntes.compareTo(original.getCustoMedioPosterior()) != 0) {
                    throw new ConflitoException("O custo atual diverge do último movimento da nota.");
                }
                BigDecimal custoDepois = original.getCustoMedioAnterior();
                if (entrada) {
                    // valorCompra registra o último preço bruto histórico, inclusive de nota revertida.
                    produto.setCusto(custoDepois);
                    produto.recalcularLucro();
                    produtos.saveAndFlush(produto);
                } else if (custoDepois.compareTo(custoAntes) != 0) {
                    throw new ConflitoException("O movimento de saída não pode alterar o custo médio.");
                }
                MovimentoEstoque inverso = aplicarMovimento(org, ator, produto, local,
                        TipoMovimentoEstoque.COMPENSACAO, original.getDelta().negate(),
                        original.getCustoUnitario(), custoAntes, custoDepois,
                        "REVERSAO_" + origem, original.getOrigemChave(), original.getId(), motivo);
                if (inverso.getSaldoAnterior().compareTo(original.getSaldoPosterior()) != 0
                        || inverso.getSaldoPosterior().compareTo(original.getSaldoAnterior()) != 0
                        || inverso.getSaldoTotalAnterior().compareTo(original.getSaldoTotalPosterior()) != 0
                        || inverso.getSaldoTotalPosterior().compareTo(original.getSaldoTotalAnterior()) != 0) {
                    throw new ConflitoException("O saldo atual diverge dos dados originais da nota.");
                }
                ids.add(inverso.getId());
            }
        }
        return List.copyOf(ids);
    }

    private MovimentoEstoque aplicarMovimento(Long organizacaoId, Long atorId, Produto produto,
            LocalEstoque local, TipoMovimentoEstoque tipo, BigDecimal delta,
            BigDecimal custoUnitario, BigDecimal custoMedioAnterior,
            BigDecimal custoMedioPosterior, String origemTipo, String origemChave,
            Long compensadoId, String motivo) {
        saldos.garantirCarrier(organizacaoId, produto.getId(), local.getId());
        SaldoEstoque saldo = saldos.buscarParaAtualizar(
                        organizacaoId, produto.getId(), local.getId())
                .orElseThrow(() -> new IllegalStateException("Carrier de saldo nao foi criado."));
        BigDecimal totalAnterior = saldos.somarPorProduto(organizacaoId, produto.getId());
        BigDecimal saldoAnterior = saldo.getQuantidade();
        Long versaoAnterior = saldo.getVersion();
        BigDecimal saldoPosterior = saldo.aplicar(delta);
        saldo = saldos.saveAndFlush(saldo);
        BigDecimal totalPosterior = totalAnterior.add(delta);

        MovimentoEstoque movimento = MovimentoEstoque.registrar(organizacaoId, produto.getId(),
                local.getId(), saldo.getId(), tipo, delta, saldoAnterior, saldoPosterior,
                custoUnitario, totalAnterior, totalPosterior, custoMedioAnterior,
                custoMedioPosterior, origemTipo, origemChave, compensadoId, atorId, motivo);
        movimento = movimentos.saveAndFlush(movimento);
        Map<String, Object> estadoAnterior = snapshotSaldo(
                produto, local, saldoAnterior, totalAnterior);
        Map<String, Object> payloadMovimento = payloadMovimento(
                movimento, produto, local, totalPosterior);
        auditoria.registrar(new RegistrarAuditoriaCommand(organizacaoId,
                tipo == TipoMovimentoEstoque.COMPENSACAO
                        ? "ESTOQUE_COMPENSADO" : "ESTOQUE_AJUSTADO",
                RECURSO, movimento.getId(), null, null, motivo, estadoAnterior,
                payloadMovimento, versaoAnterior, saldo.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
        publicar(movimento, evento(tipo), payloadMovimento);
        if (totalAnterior.compareTo(produto.getQuantidadeMinima()) > 0
                && totalPosterior.compareTo(produto.getQuantidadeMinima()) <= 0) {
            publicar(movimento, "EstoqueMinimoAtingido",
                    payloadLimiar(movimento, produto, totalPosterior));
        }
        return movimento;
    }

    private void bloquearProduto(Long organizacaoId, Long produtoId) {
        String escopo = "estoque:" + organizacaoId + ":" + produtoId;
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                ps -> ps.setString(1, escopo), rs -> null);
        boolean existe = !jdbc.queryForList("""
                SELECT id FROM produto
                WHERE id=? AND organizacao_id=? AND ativo=true
                FOR KEY SHARE
                """, Long.class, produtoId, organizacaoId).isEmpty();
        if (!existe) {
            throw new RecursoNaoEncontradoException("Produto ativo", produtoId);
        }
    }

    private Produto buscarProdutoAtivo(Long organizacaoId, Long produtoId) {
        return produtos.findByIdAndOrganizacaoId(produtoId, organizacaoId)
                .filter(produto -> Boolean.TRUE.equals(produto.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto ativo", produtoId));
    }

    private LocalEstoque buscarLocalAtivo(Long organizacaoId, Long localId) {
        return locais.findAtivoParaMovimento(localId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "LocalEstoque ativo", localId));
    }

    private void publicar(MovimentoEstoque movimento, String evento,
            Map<String, Object> payload) {
        outbox.publicar(new PublicarEventoOutboxCommand(evento, movimento.getOrganizacaoId(),
                RECURSO, movimento.getId(), 0L, null, 1, payload));
    }

    private static String evento(TipoMovimentoEstoque tipo) {
        return switch (tipo) {
            case RECEBIMENTO -> "EstoqueRecebido";
            case BAIXA -> "EstoqueBaixado";
            case AJUSTE, COMPENSACAO -> "EstoqueAjustado";
        };
    }

    private static Map<String, Object> snapshotSaldo(Produto produto, LocalEstoque local,
            BigDecimal saldoLocal, BigDecimal saldoTotal) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("produtoId", produto.getId());
        snapshot.put("localEstoqueId", local.getId());
        snapshot.put("saldoLocal", saldoLocal);
        snapshot.put("saldoTotal", saldoTotal);
        snapshot.put("quantidadeMinima", produto.getQuantidadeMinima());
        return snapshot;
    }

    private static Map<String, Object> payloadMovimento(MovimentoEstoque movimento,
            Produto produto, LocalEstoque local, BigDecimal saldoTotal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movimentoId", movimento.getId());
        payload.put("produtoId", produto.getId());
        payload.put("produto", produto.getNome());
        payload.put("localEstoqueId", local.getId());
        payload.put("localEstoque", local.getNome());
        payload.put("saldoEstoqueId", movimento.getSaldoEstoqueId());
        payload.put("tipo", movimento.getTipo().name());
        payload.put("delta", movimento.getDelta());
        payload.put("custoUnitario", movimento.getCustoUnitario());
        payload.put("saldoTotalAnterior", movimento.getSaldoTotalAnterior());
        payload.put("saldoTotalPosterior", movimento.getSaldoTotalPosterior());
        payload.put("custoMedioAnterior", movimento.getCustoMedioAnterior());
        payload.put("custoMedioPosterior", movimento.getCustoMedioPosterior());
        payload.put("origemTipo", movimento.getOrigemTipo());
        payload.put("origemChave", movimento.getOrigemChave());
        payload.put("atorId", movimento.getAtorId());
        payload.put("motivo", movimento.getMotivo());
        payload.put("saldoAnterior", movimento.getSaldoAnterior());
        payload.put("saldoPosterior", movimento.getSaldoPosterior());
        payload.put("saldoTotal", saldoTotal);
        payload.put("quantidadeMinima", produto.getQuantidadeMinima());
        payload.put("compensadoId", movimento.getCompensadoId());
        return payload;
    }

    private static Map<String, Object> payloadLimiar(MovimentoEstoque movimento,
            Produto produto, BigDecimal saldoTotal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movimentoId", movimento.getId());
        payload.put("produtoId", produto.getId());
        payload.put("produto", produto.getNome());
        payload.put("saldoTotal", saldoTotal);
        payload.put("quantidadeMinima", produto.getQuantidadeMinima());
        return payload;
    }

    private static void validarDelta(BigDecimal delta) {
        if (delta == null || delta.signum() == 0) {
            throw new IllegalArgumentException("O delta deve ser diferente de zero.");
        }
    }

}
