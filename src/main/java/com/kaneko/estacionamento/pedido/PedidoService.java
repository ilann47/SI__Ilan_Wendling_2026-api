package com.kaneko.estacionamento.pedido;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.inventario.reserva.ReservaInventarioService;
import com.kaneko.estacionamento.inventario.reserva.ReservaParaPedido;
import com.kaneko.estacionamento.pedido.dto.ConfirmarPedidoManualmenteRequest;
import com.kaneko.estacionamento.pedido.dto.CriarPedidoRequest;
import com.kaneko.estacionamento.pedido.dto.PedidoResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {
    private static final String OPERACAO_CRIAR = "PEDIDO_CRIAR";
    private static final String OPERACAO_CONFIRMAR_MANUAL = "PEDIDO_CONFIRMAR_MANUAL";
    private static final String RECURSO = "PEDIDO";

    private final PedidoRepository pedidos;
    private final ConfirmacaoManualPedidoRepository confirmacoesManuais;
    private final ReservaInventarioService reservas;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public PedidoService(
            PedidoRepository pedidos,
            ConfirmacaoManualPedidoRepository confirmacoesManuais,
            ReservaInventarioService reservas,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria,
            OutboxService outbox) {
        this.pedidos = pedidos;
        this.confirmacoesManuais = confirmacoesManuais;
        this.reservas = reservas;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional
    public PedidoResponse criar(
            Long organizacaoId, Long atorId, String chaveIdempotencia,
            CriarPedidoRequest request) {
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
            return PedidoResponse.from(pedidos.findByIdAndOrganizacaoIdAndAtorId(
                            anterior.get().recursoId(), organizacaoId, atorId)
                    .orElseThrow(() -> new ConflitoException(
                            "O resultado idempotente nao esta mais disponivel.")));
        }

        ReservaParaPedido reserva = reservas.obterMantidaParaPedido(
                organizacaoId, atorId, request.inventoryHoldId());
        if (!reserva.canal().equals(request.channel())) {
            throw new ConflitoException(
                    "O canal do pedido diverge do canal da reserva de inventario.");
        }
        if (pedidos.existsByReservaInventarioId(reserva.reservaId())) {
            throw new ConflitoException("A reserva de inventario ja possui um pedido.");
        }

        Pedido pedido = Pedido.criar(
                organizacaoId, reserva.eventoId(), reserva.reservaId(), atorId,
                numero(), request.channel(), request.buyer().name(),
                request.buyer().email(), request.buyer().document(),
                request.vehiclePlate(), reserva.expiraEm());
        pedido.adicionarItem(ItemPedido.criar(
                pedido, reserva.produtoId(), reserva.alocacaoId(),
                reserva.lotePrecoId(), reserva.nomeProduto(), reserva.categoria(),
                reserva.direito(), reserva.inicioAcesso(), reserva.fimAcesso(),
                reserva.quantidade(), reserva.precoUnitario(), reserva.moeda()));
        pedido = pedidos.saveAndFlush(pedido);
        Map<String, Object> criado = snapshot(pedido);
        outbox.publicar(new PublicarEventoOutboxCommand(
                "PedidoCriado", organizacaoId, RECURSO, pedido.getId(),
                pedido.getVersion(), null, 1, criado));

        pedido.aguardarPagamento();
        pedidos.flush();
        Map<String, Object> aguardando = snapshot(pedido);
        outbox.publicar(new PublicarEventoOutboxCommand(
                "PedidoAguardandoPagamento", organizacaoId, RECURSO, pedido.getId(),
                pedido.getVersion(), null, 1, aguardando));
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "PEDIDO_CRIADO", RECURSO, pedido.getId(),
                pedido.getEventoId(), null, null, null, aguardando,
                null, pedido.getVersion(), ClasseRetencaoAuditoria.FINANCEIRA));
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO_CRIAR, chave, requestHash,
                RECURSO, pedido.getId());
        return PedidoResponse.from(pedido);
    }

    @Transactional(readOnly = true)
    public PedidoResponse consultar(Long organizacaoId, Long atorId, Long pedidoId) {
        return PedidoResponse.from(pedidos.findByIdAndOrganizacaoIdAndAtorId(
                        pedidoId, organizacaoId, atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Pedido", pedidoId)));
    }

    @Transactional
    public PedidoResponse confirmarManualmente(
            Long organizacaoId, Long atorId, Long pedidoId, Long versaoEsperada,
            String chaveIdempotencia, ConfirmarPedidoManualmenteRequest request) {
        String chave = validarChave(chaveIdempotencia);
        validarConfirmacaoManual(request);
        String requestHash = hashConfirmacaoManual(pedidoId, request);
        idempotencia.bloquear(
                organizacaoId, atorId, OPERACAO_CONFIRMAR_MANUAL, chave);
        var anterior = idempotencia.buscar(
                organizacaoId, atorId, OPERACAO_CONFIRMAR_MANUAL, chave);
        if (anterior.isPresent()) {
            if (!requestHash.equals(anterior.get().requestHash())) {
                throw new ConflitoException(
                        "A chave de idempotencia ja foi usada com outro payload.");
            }
            return PedidoResponse.from(pedidos.findByIdAndOrganizacaoId(
                            anterior.get().recursoId(), organizacaoId)
                    .orElseThrow(() -> new ConflitoException(
                            "O resultado idempotente nao esta mais disponivel.")));
        }

        Pedido pedido = pedidos.findByIdAndOrganizacaoIdForUpdate(
                        pedidoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Pedido", pedidoId));
        if (!pedido.getVersion().equals(versaoEsperada)) {
            throw new VersaoPedidoDivergenteException();
        }
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new ConflitoException(
                    "Somente pedido aguardando pagamento pode ser confirmado manualmente.");
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (!agora.toInstant().isBefore(pedido.getExpiraEm().toInstant())) {
            throw new ConflitoException("Pedido vencido nao pode ser confirmado.");
        }
        if (confirmacoesManuais.findByPedidoIdAndOrganizacaoId(
                pedidoId, organizacaoId).isPresent()) {
            throw new ConflitoException("O pedido ja possui confirmacao manual.");
        }

        Map<String, Object> estadoAnterior = snapshot(pedido);
        Long versaoAnterior = pedido.getVersion();
        reservas.confirmarParaPedido(
                organizacaoId, pedido.getReservaInventarioId(), pedido.getEventoId());
        ConfirmacaoManualPedido confirmacao = confirmacoesManuais.saveAndFlush(
                ConfirmacaoManualPedido.criar(
                        organizacaoId, pedido, atorId, request.paymentMethod(),
                        request.evidenceReference(), request.reason()));
        pedido.confirmar();
        pedidos.flush();
        Map<String, Object> estadoPosterior = snapshot(pedido);
        estadoPosterior.put("confirmacaoManualId", confirmacao.getId());
        estadoPosterior.put("meioPagamento", confirmacao.getMeioPagamento());
        estadoPosterior.put(
                "statusConciliacao", confirmacao.getStatusConciliacao().name());
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "PEDIDO_CONFIRMADO_MANUALMENTE", RECURSO,
                pedido.getId(), pedido.getEventoId(), null, confirmacao.getMotivo(),
                estadoAnterior, estadoPosterior, versaoAnterior, pedido.getVersion(),
                ClasseRetencaoAuditoria.FINANCEIRA));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "PedidoConfirmado", organizacaoId, RECURSO, pedido.getId(),
                pedido.getVersion(), null, 1, estadoPosterior));
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO_CONFIRMAR_MANUAL, chave,
                requestHash, RECURSO, pedido.getId());
        return PedidoResponse.from(pedido);
    }

    @Transactional
    public int expirarVencidos(int limite) {
        if (limite < 1 || limite > 1000) {
            throw new IllegalArgumentException("O limite de expiracao e invalido.");
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        var ids = pedidos.buscarIdsExpirados(
                List.of(StatusPedido.CRIADO, StatusPedido.AGUARDANDO_PAGAMENTO),
                agora, PageRequest.of(0, limite));
        int total = 0;
        for (Long id : ids) {
            Pedido pedido = pedidos.findByIdForUpdate(id).orElse(null);
            if (pedido == null || (pedido.getStatus() != StatusPedido.CRIADO
                    && pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO)) {
                continue;
            }
            Map<String, Object> anterior = snapshot(pedido);
            Long versaoAnterior = pedido.getVersion();
            if (!pedido.expirar(agora)) continue;
            pedidos.flush();
            Map<String, Object> posterior = snapshot(pedido);
            auditoria.registrar(new RegistrarAuditoriaCommand(
                    pedido.getOrganizacaoId(), "PEDIDO_EXPIRADO", RECURSO,
                    pedido.getId(), pedido.getEventoId(), null, "PRAZO_EXPIRADO",
                    anterior, posterior, versaoAnterior, pedido.getVersion(),
                    ClasseRetencaoAuditoria.FINANCEIRA));
            outbox.publicar(new PublicarEventoOutboxCommand(
                    "PedidoExpirado", pedido.getOrganizacaoId(), RECURSO,
                    pedido.getId(), pedido.getVersion(), null, 1, posterior));
            total++;
        }
        return total;
    }

    private static void validarRequest(CriarPedidoRequest request) {
        if (request == null || request.inventoryHoldId() == null
                || request.buyer() == null || request.channel() == null
                || request.channel().isBlank()) {
            throw new IllegalArgumentException("O pedido esta incompleto.");
        }
    }

    private static void validarConfirmacaoManual(
            ConfirmarPedidoManualmenteRequest request) {
        if (request == null || request.paymentMethod() == null
                || request.paymentMethod().isBlank()
                || request.evidenceReference() == null
                || request.evidenceReference().isBlank()
                || request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException(
                    "A confirmacao manual esta incompleta.");
        }
    }

    private static String numero() {
        return "PED-" + UUID.randomUUID().toString().replace("-", "")
                .toUpperCase(Locale.ROOT);
    }

    private static String validarChave(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "O cabecalho Idempotency-Key e obrigatorio.");
        }
        String chave = valor.trim();
        if (chave.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key deve ter no maximo 100 caracteres.");
        }
        return chave;
    }

    private static String hash(CriarPedidoRequest request) {
        String canonico = campo(request.inventoryHoldId())
                + campo(request.buyer().name().trim())
                + campo(request.buyer().email().trim().toLowerCase(Locale.ROOT))
                + campo(request.buyer().document() == null ? null
                        : request.buyer().document().replaceAll("[^0-9]", ""))
                + campo(request.vehiclePlate() == null ? null
                        : request.vehiclePlate().replaceAll("[^A-Za-z0-9]", "")
                                .toUpperCase(Locale.ROOT))
                + campo(request.channel().trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel.", ex);
        }
    }

    private static String hashConfirmacaoManual(
            Long pedidoId, ConfirmarPedidoManualmenteRequest request) {
        String canonico = campo(pedidoId)
                + campo(request.paymentMethod().trim().toUpperCase(Locale.ROOT))
                + campo(request.evidenceReference().trim())
                + campo(request.reason().trim());
        return sha256(canonico);
    }

    private static String sha256(String canonico) {
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

    private static Map<String, Object> snapshot(Pedido pedido) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("numero", pedido.getNumero());
        estado.put("eventoId", pedido.getEventoId());
        estado.put("reservaInventarioId", pedido.getReservaInventarioId());
        estado.put("status", pedido.getStatus().name());
        estado.put("subtotal", pedido.getSubtotal());
        estado.put("desconto", pedido.getDesconto());
        estado.put("taxa", pedido.getTaxa());
        estado.put("total", pedido.getTotal());
        estado.put("moeda", pedido.getMoeda());
        estado.put("expiraEm", pedido.getExpiraEm().toInstant().toString());
        return estado;
    }
}
