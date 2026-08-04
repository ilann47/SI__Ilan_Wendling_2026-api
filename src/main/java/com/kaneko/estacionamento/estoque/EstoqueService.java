package com.kaneko.estacionamento.estoque;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.estoque.dto.AjusteEstoqueRequest;
import com.kaneko.estacionamento.estoque.dto.CompensarMovimentoRequest;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.estoque.dto.PosicaoEstoqueResponse;
import com.kaneko.estacionamento.estoque.dto.SaldoEstoqueResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstoqueService {
    private static final String OPERACAO_AJUSTAR = "ESTOQUE_AJUSTAR";
    private static final String OPERACAO_COMPENSAR = "ESTOQUE_COMPENSAR";
    private static final String RECURSO = "MOVIMENTO_ESTOQUE";

    private final ProdutoRepository produtos;
    private final LocalEstoqueRepository locais;
    private final SaldoEstoqueRepository saldos;
    private final MovimentoEstoqueRepository movimentos;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;
    private final JdbcTemplate jdbc;

    public EstoqueService(ProdutoRepository produtos, LocalEstoqueRepository locais,
            SaldoEstoqueRepository saldos, MovimentoEstoqueRepository movimentos,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria,
            OutboxService outbox, JdbcTemplate jdbc) {
        this.produtos = produtos;
        this.locais = locais;
        this.saldos = saldos;
        this.movimentos = movimentos;
        this.idempotencia = idempotencia;
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

    @Transactional
    public MovimentoEstoqueResponse ajustar(Long organizacaoId, Long atorId,
            String chaveIdempotencia, AjusteEstoqueRequest request) {
        String chave = validarChave(chaveIdempotencia);
        validarDelta(request.delta());
        String requestHash = hash(request.produtoId(), request.localEstoqueId(),
                decimal(request.delta()), decimal(request.custoUnitario()), request.motivo().trim());
        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_AJUSTAR, chave);
        var anterior = idempotencia.buscar(organizacaoId, atorId, OPERACAO_AJUSTAR, chave);
        if (anterior.isPresent()) {
            conferirHash(anterior.get().requestHash(), requestHash);
            return buscarMovimento(organizacaoId, anterior.get().recursoId());
        }

        bloquearProduto(organizacaoId, request.produtoId());
        Produto produto = buscarProdutoAtivo(organizacaoId, request.produtoId());
        LocalEstoque local = buscarLocalAtivo(organizacaoId, request.localEstoqueId());
        MovimentoEstoque movimento = aplicarMovimento(organizacaoId, atorId, produto, local,
                TipoMovimentoEstoque.AJUSTE, request.delta(), request.custoUnitario(),
                "AJUSTE_MANUAL", atorId + ":" + chave, null, request.motivo());
        idempotencia.registrar(organizacaoId, atorId, OPERACAO_AJUSTAR, chave, requestHash,
                RECURSO, movimento.getId());
        return buscarMovimento(organizacaoId, movimento.getId());
    }

    /** Registra o recebimento confirmado por um documento fiscal do mesmo tenant. */
    @Transactional
    public Long registrarRecebimento(Long organizacaoId, Long atorId, Long produtoId,
            Long localId, BigDecimal quantidade, BigDecimal custoUnitario,
            String origemTipo, String origemChave, String motivo) {
        validarDelta(quantidade);
        if (quantidade.signum() <= 0) {
            throw new IllegalArgumentException("A quantidade recebida deve ser positiva.");
        }
        bloquearProduto(organizacaoId, produtoId);
        Produto produto = buscarProdutoAtivo(organizacaoId, produtoId);
        LocalEstoque local = buscarLocalAtivo(organizacaoId, localId);
        return aplicarMovimento(organizacaoId, atorId, produto, local,
                TipoMovimentoEstoque.RECEBIMENTO, quantidade, custoUnitario,
                origemTipo, origemChave, null, motivo).getId();
    }

    @Transactional
    public MovimentoEstoqueResponse compensar(Long organizacaoId, Long atorId,
            Long movimentoId, String chaveIdempotencia, CompensarMovimentoRequest request) {
        String chave = validarChave(chaveIdempotencia);
        String requestHash = hash(movimentoId, request.motivo().trim());
        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_COMPENSAR, chave);
        var anterior = idempotencia.buscar(organizacaoId, atorId, OPERACAO_COMPENSAR, chave);
        if (anterior.isPresent()) {
            conferirHash(anterior.get().requestHash(), requestHash);
            return buscarMovimento(organizacaoId, anterior.get().recursoId());
        }

        MovimentoEstoque original = buscarMovimentoEntidade(organizacaoId, movimentoId);
        bloquearProduto(organizacaoId, original.getProdutoId());
        if (movimentos.existsByCompensadoId(original.getId())) {
            throw new ConflitoException("O movimento ja possui compensacao.");
        }
        Produto produto = buscarProdutoAtivo(organizacaoId, original.getProdutoId());
        LocalEstoque local = buscarLocalAtivo(organizacaoId, original.getLocalEstoqueId());
        MovimentoEstoque compensacao = aplicarMovimento(organizacaoId, atorId, produto, local,
                TipoMovimentoEstoque.COMPENSACAO, original.getDelta().negate(),
                original.getCustoUnitario(), "COMPENSACAO_MOVIMENTO",
                atorId + ":" + movimentoId + ":" + chave,
                original.getId(), request.motivo());
        idempotencia.registrar(organizacaoId, atorId, OPERACAO_COMPENSAR, chave, requestHash,
                RECURSO, compensacao.getId());
        return buscarMovimento(organizacaoId, compensacao.getId());
    }

    private MovimentoEstoque aplicarMovimento(Long organizacaoId, Long atorId, Produto produto,
            LocalEstoque local, TipoMovimentoEstoque tipo, BigDecimal delta,
            BigDecimal custoUnitario, String origemTipo, String origemChave,
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

        MovimentoEstoque movimento = MovimentoEstoque.registrar(organizacaoId, produto.getId(),
                local.getId(), saldo.getId(), tipo, delta, saldoAnterior, saldoPosterior,
                custoUnitario, origemTipo, origemChave, compensadoId, atorId, motivo);
        movimento = movimentos.saveAndFlush(movimento);
        BigDecimal totalPosterior = totalAnterior.add(delta);
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

    private MovimentoEstoque buscarMovimentoEntidade(Long organizacaoId, Long id) {
        return movimentos.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("MovimentoEstoque", id));
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

    private static String validarChave(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O cabecalho Idempotency-Key e obrigatorio.");
        }
        String chave = valor.trim();
        if (chave.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key deve ter no maximo 100 caracteres.");
        }
        return chave;
    }

    private static void conferirHash(String anterior, String atual) {
        if (!atual.equals(anterior)) {
            throw new ConflitoException("A chave de idempotencia ja foi usada com outro payload.");
        }
    }

    private static String decimal(BigDecimal valor) {
        return valor == null ? "null" : valor.stripTrailingZeros().toPlainString();
    }

    private static String hash(Object... partes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object parte : partes) {
                digest.update(String.valueOf(parte).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel.", ex);
        }
    }
}
