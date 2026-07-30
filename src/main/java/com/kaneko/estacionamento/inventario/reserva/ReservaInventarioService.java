package com.kaneko.estacionamento.inventario.reserva;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.evento.Evento;
import com.kaneko.estacionamento.evento.EventoRepository;
import com.kaneko.estacionamento.evento.StatusEvento;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEvento;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoRepository;
import com.kaneko.estacionamento.inventario.produto.LotePreco;
import com.kaneko.estacionamento.inventario.produto.LotePrecoRepository;
import com.kaneko.estacionamento.inventario.produto.ProdutoEstacionamento;
import com.kaneko.estacionamento.inventario.produto.ProdutoEstacionamentoRepository;
import com.kaneko.estacionamento.inventario.produto.StatusProdutoEstacionamento;
import com.kaneko.estacionamento.inventario.reserva.dto.CriarReservaInventarioRequest;
import com.kaneko.estacionamento.inventario.reserva.dto.LiberarReservaInventarioRequest;
import com.kaneko.estacionamento.inventario.reserva.dto.ReservaInventarioResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservaInventarioService {
    private static final String OPERACAO_CRIAR = "RESERVA_INVENTARIO_CRIAR";
    private static final String RECURSO = "RESERVA_INVENTARIO";

    private final ReservaInventarioRepository reservas;
    private final ProdutoEstacionamentoRepository produtos;
    private final EventoRepository eventos;
    private final LotePrecoRepository lotes;
    private final AlocacaoPatioEventoRepository alocacoes;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;
    private final long duracaoMinutos;

    public ReservaInventarioService(
            ReservaInventarioRepository reservas,
            ProdutoEstacionamentoRepository produtos, EventoRepository eventos,
            LotePrecoRepository lotes, AlocacaoPatioEventoRepository alocacoes,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria,
            OutboxService outbox,
            @Value("${app.inventory.hold-duration-minutes:15}") long duracaoMinutos) {
        if (duracaoMinutos < 1 || duracaoMinutos > 1440) {
            throw new IllegalArgumentException(
                    "A duracao do hold deve estar entre 1 e 1440 minutos.");
        }
        this.reservas = reservas;
        this.produtos = produtos;
        this.eventos = eventos;
        this.lotes = lotes;
        this.alocacoes = alocacoes;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
        this.duracaoMinutos = duracaoMinutos;
    }

    @Transactional
    public ReservaInventarioResponse criar(
            Long organizacaoId, Long atorId, String chaveIdempotencia,
            CriarReservaInventarioRequest request) {
        String chave = validarChave(chaveIdempotencia);
        validarRequest(request);
        String requestHash = hash(request);
        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_CRIAR, chave);
        var anterior = idempotencia.buscar(
                organizacaoId, atorId, OPERACAO_CRIAR, chave);
        if (anterior.isPresent()) {
            if (!requestHash.equals(anterior.get().requestHash())) {
                throw new ConflitoException(
                        "A chave de idempotencia ja foi usada com outro payload.");
            }
            return ReservaInventarioResponse.from(
                    reservas.findByIdAndOrganizacaoIdAndAtorId(
                                    anterior.get().recursoId(), organizacaoId, atorId)
                            .orElseThrow(() -> new ConflitoException(
                                    "O resultado idempotente nao esta mais disponivel.")));
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        ProdutoEstacionamento produto = produtos.findByIdAndOrganizacaoIdForUpdate(
                        request.parkingProductId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto de estacionamento", request.parkingProductId()));
        if (produto.getStatus() != StatusProdutoEstacionamento.PUBLICADO) {
            throw new ConflitoException("O produto nao esta publicado para venda.");
        }
        Evento evento = eventos.findByIdAndOrganizacaoId(
                        produto.getEventoId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Evento", produto.getEventoId()));
        if (evento.getStatus() != StatusEvento.VENDAS_ABERTAS) {
            throw new ConflitoException("O evento nao esta com vendas abertas.");
        }

        expirarDoProduto(produto, agora);
        LotePreco lote = selecionarLote(produto.getId(), agora);
        int limite = Math.min(produto.getQuota(), lote.getQuantidade());
        int comprometido = produto.getQuantidadeReservada()
                + produto.getQuantidadeVendida() + produto.getQuantidadeBloqueada();
        if (request.quantity() > limite - comprometido) {
            throw new ConflitoException("Nao ha quantidade disponivel para a reserva.");
        }
        produto.reservar(request.quantity(), limite);
        ReservaInventario reserva = ReservaInventario.criar(
                organizacaoId, produto.getEventoId(), produto.getId(), atorId,
                request.quantity(), request.channel(), request.buyerReference(), chave,
                lote.getId(), lote.getPreco(), lote.getMoeda(),
                agora.plusMinutes(duracaoMinutos));
        reserva = reservas.saveAndFlush(reserva);
        produtos.flush();
        registrarCriacao(reserva, produto);
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO_CRIAR, chave, requestHash,
                RECURSO, reserva.getId());
        return ReservaInventarioResponse.from(reserva);
    }

    @Transactional
    public ReservaInventarioResponse consultar(
            Long organizacaoId, Long atorId, Long reservaId) {
        ReservaInventario visivel = reservas.findByIdAndOrganizacaoIdAndAtorId(
                        reservaId, organizacaoId, atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Reserva de inventario", reservaId));
        if (visivel.getStatus() != StatusReservaInventario.MANTIDA
                || OffsetDateTime.now(ZoneOffset.UTC).toInstant()
                        .isBefore(visivel.getExpiraEm().toInstant())) {
            return ReservaInventarioResponse.from(visivel);
        }
        ProdutoEstacionamento produto = produtos.findByIdAndOrganizacaoIdForUpdate(
                        visivel.getProdutoId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto de estacionamento", visivel.getProdutoId()));
        ReservaInventario reserva = reservas.findDoAtorForUpdate(
                        reservaId, organizacaoId, atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Reserva de inventario", reservaId));
        expirar(reserva, produto, OffsetDateTime.now(ZoneOffset.UTC));
        return ReservaInventarioResponse.from(reserva);
    }

    @Transactional
    public ReservaInventarioResponse liberar(
            Long organizacaoId, Long atorId, Long reservaId,
            LiberarReservaInventarioRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("O comando de liberacao e obrigatorio.");
        }
        ReservaInventario visivel = reservas.findByIdAndOrganizacaoIdAndAtorId(
                        reservaId, organizacaoId, atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Reserva de inventario", reservaId));
        ProdutoEstacionamento produto = produtos.findByIdAndOrganizacaoIdForUpdate(
                        visivel.getProdutoId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto de estacionamento", visivel.getProdutoId()));
        ReservaInventario reserva = reservas.findDoAtorForUpdate(
                        reservaId, organizacaoId, atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Reserva de inventario", reservaId));
        if (reserva.getStatus() == StatusReservaInventario.LIBERADA) {
            return ReservaInventarioResponse.from(reserva);
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (!agora.toInstant().isBefore(reserva.getExpiraEm().toInstant())) {
            expirar(reserva, produto, agora);
            return ReservaInventarioResponse.from(reserva);
        }
        if (reserva.getStatus() != StatusReservaInventario.MANTIDA) {
            throw new ConflitoException("A reserva nao pode mais ser liberada.");
        }
        Map<String, Object> anterior = snapshot(reserva);
        Long versaoAnterior = reserva.getVersion();
        reserva.liberar(request.reason(), agora);
        produto.liberarReserva(reserva.getQuantidade());
        reservas.flush();
        produtos.flush();
        registrarTransicao(
                reserva, produto, "RESERVA_INVENTARIO_LIBERADA", "InventarioLiberado",
                anterior, versaoAnterior);
        return ReservaInventarioResponse.from(reserva);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReservaParaPedido obterMantidaParaPedido(
            Long organizacaoId, Long atorId, Long reservaId) {
        ReservaInventario reserva = reservas.findDoAtorForUpdate(
                        reservaId, organizacaoId, atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Reserva de inventario", reservaId));
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (reserva.getStatus() != StatusReservaInventario.MANTIDA
                || !agora.toInstant().isBefore(reserva.getExpiraEm().toInstant())) {
            throw new ConflitoException(
                    "A reserva de inventario nao esta mantida ou ja expirou.");
        }
        ProdutoEstacionamento produto = produtos.findByIdAndOrganizacaoId(
                        reserva.getProdutoId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto de estacionamento", reserva.getProdutoId()));
        return new ReservaParaPedido(
                reserva.getId(), reserva.getEventoId(), produto.getId(),
                produto.getAlocacaoId(), reserva.getLotePrecoId(), produto.getNome(),
                produto.getCategoria(), produto.getDireito(), produto.getInicioAcesso(),
                produto.getFimAcesso(), reserva.getQuantidade(), reserva.getCanal(),
                reserva.getPrecoUnitario(), reserva.getMoeda(), reserva.getExpiraEm());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReservaInventarioResponse confirmarParaPedido(
            Long organizacaoId, Long reservaId, Long eventoId) {
        ReservaInventario visivel = reservas.findByIdAndOrganizacaoId(
                        reservaId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Reserva de inventario", reservaId));
        if (!visivel.getEventoId().equals(eventoId)) {
            throw new RecursoNaoEncontradoException(
                    "Reserva de inventario", reservaId);
        }
        ProdutoEstacionamento produto = produtos.findByIdAndOrganizacaoIdForUpdate(
                        visivel.getProdutoId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto de estacionamento", visivel.getProdutoId()));
        ReservaInventario reserva = reservas.findByIdAndOrganizacaoIdForUpdate(
                        reservaId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Reserva de inventario", reservaId));
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (reserva.getStatus() != StatusReservaInventario.MANTIDA
                || !agora.toInstant().isBefore(reserva.getExpiraEm().toInstant())) {
            throw new ConflitoException(
                    "A reserva nao pode ser confirmada porque nao esta mantida ou venceu.");
        }
        Map<String, Object> anterior = snapshot(reserva);
        Long versaoAnterior = reserva.getVersion();
        reserva.confirmar(agora);
        produto.confirmarReserva(reserva.getQuantidade());
        reservas.flush();
        produtos.flush();
        registrarTransicao(
                reserva, produto, "RESERVA_INVENTARIO_CONFIRMADA",
                "InventarioConfirmado", anterior, versaoAnterior);
        return ReservaInventarioResponse.from(reserva);
    }

    @Transactional
    public int expirarVencidas(int limite) {
        if (limite < 1 || limite > 1000) {
            throw new IllegalArgumentException("O limite de expiracao e invalido.");
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<Long> ids = reservas.buscarIdsExpirados(
                StatusReservaInventario.MANTIDA, agora, PageRequest.of(0, limite));
        int total = 0;
        for (Long id : ids) {
            ReservaInventario candidato = reservas.findById(id).orElse(null);
            if (candidato == null || candidato.getStatus() != StatusReservaInventario.MANTIDA) {
                continue;
            }
            ProdutoEstacionamento produto = produtos.findByIdAndOrganizacaoIdForUpdate(
                            candidato.getProdutoId(), candidato.getOrganizacaoId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Produto da reserva expirada nao foi encontrado."));
            candidato = reservas.findByIdForUpdate(id).orElse(null);
            if (candidato == null || candidato.getStatus() != StatusReservaInventario.MANTIDA) {
                continue;
            }
            if (expirar(candidato, produto, agora)) {
                total++;
            }
        }
        return total;
    }

    private void expirarDoProduto(
            ProdutoEstacionamento produto, OffsetDateTime agora) {
        for (ReservaInventario reserva : reservas.buscarExpiradasDoProdutoForUpdate(
                produto.getId(), StatusReservaInventario.MANTIDA, agora)) {
            expirar(reserva, produto, agora);
        }
    }

    private boolean expirar(
            ReservaInventario reserva, ProdutoEstacionamento produto,
            OffsetDateTime agora) {
        Map<String, Object> anterior = snapshot(reserva);
        Long versaoAnterior = reserva.getVersion();
        if (!reserva.expirar(agora)) {
            return false;
        }
        produto.liberarReserva(reserva.getQuantidade());
        reservas.flush();
        produtos.flush();
        registrarTransicao(
                reserva, produto, "RESERVA_INVENTARIO_EXPIRADA", "ReservaExpirada",
                anterior, versaoAnterior);
        outbox.publicar(new PublicarEventoOutboxCommand(
                "InventarioLiberado", reserva.getOrganizacaoId(), RECURSO,
                reserva.getId(), reserva.getVersion(), null, 1, snapshot(reserva)));
        return true;
    }

    private LotePreco selecionarLote(Long produtoId, OffsetDateTime agora) {
        return lotes.buscarVigentesOrdenadosPorPrioridade(List.of(produtoId), agora)
                .stream().findFirst()
                .orElseThrow(() -> new ConflitoException(
                        "Nao existe lote de preco vigente para o produto."));
    }

    private void registrarCriacao(
            ReservaInventario reserva, ProdutoEstacionamento produto) {
        Map<String, Object> estado = snapshot(reserva);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                reserva.getOrganizacaoId(), "RESERVA_INVENTARIO_CRIADA", RECURSO,
                reserva.getId(), reserva.getEventoId(), patioId(produto), null,
                null, estado, null, reserva.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "InventarioReservado", reserva.getOrganizacaoId(), RECURSO,
                reserva.getId(), reserva.getVersion(), null, 1, estado));
    }

    private void registrarTransicao(
            ReservaInventario reserva, ProdutoEstacionamento produto,
            String acao, String evento, Map<String, Object> anterior,
            Long versaoAnterior) {
        Map<String, Object> posterior = snapshot(reserva);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                reserva.getOrganizacaoId(), acao, RECURSO, reserva.getId(),
                reserva.getEventoId(), patioId(produto), null, anterior, posterior,
                versaoAnterior, reserva.getVersion(), ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand(
                evento, reserva.getOrganizacaoId(), RECURSO, reserva.getId(),
                reserva.getVersion(), null, 1, posterior));
    }

    private Long patioId(ProdutoEstacionamento produto) {
        return alocacoes.findByIdAndOrganizacaoId(
                        produto.getAlocacaoId(), produto.getOrganizacaoId())
                .map(AlocacaoPatioEvento::getPatioId)
                .orElse(null);
    }

    private static void validarRequest(CriarReservaInventarioRequest request) {
        if (request == null || request.parkingProductId() == null
                || request.quantity() == null || request.quantity() < 1
                || request.channel() == null || request.channel().isBlank()) {
            throw new IllegalArgumentException("A reserva de inventario esta incompleta.");
        }
    }

    private static String validarChave(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O cabecalho Idempotency-Key e obrigatorio.");
        }
        String chave = valor.trim();
        if (chave.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key deve ter no maximo 100 caracteres.");
        }
        return chave;
    }

    private static String hash(CriarReservaInventarioRequest request) {
        String canonico = campo(request.parkingProductId()) + campo(request.quantity())
                + campo(request.channel().trim())
                + campo(request.buyerReference() == null
                        ? null : request.buyerReference().trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel.", ex);
        }
    }

    private static String campo(Object valor) {
        String texto = String.valueOf(valor);
        return texto.length() + ":" + texto;
    }

    private static Map<String, Object> snapshot(ReservaInventario reserva) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("eventoId", reserva.getEventoId());
        estado.put("produtoId", reserva.getProdutoId());
        estado.put("quantidade", reserva.getQuantidade());
        estado.put("canal", reserva.getCanal());
        estado.put("lotePrecoId", reserva.getLotePrecoId());
        estado.put("precoUnitario", reserva.getPrecoUnitario());
        estado.put("moeda", reserva.getMoeda());
        estado.put("status", reserva.getStatus().name());
        estado.put("expiraEm", reserva.getExpiraEm().toInstant().toString());
        if (reserva.getFinalizadaEm() != null) {
            estado.put("finalizadaEm", reserva.getFinalizadaEm().toInstant().toString());
            estado.put("motivoFinalizacao", reserva.getMotivoFinalizacao());
        }
        return estado;
    }
}
