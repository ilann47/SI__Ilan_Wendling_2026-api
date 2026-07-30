package com.kaneko.estacionamento.credencial;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.credencial.dto.CredencialAcessoResponse;
import com.kaneko.estacionamento.credencial.dto.EmitirCredencialRequest;
import com.kaneko.estacionamento.pedido.ItemPedido;
import com.kaneko.estacionamento.pedido.Pedido;
import com.kaneko.estacionamento.pedido.PedidoRepository;
import com.kaneko.estacionamento.pedido.StatusPedido;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredencialAcessoService {
    private static final String OPERACAO_EMITIR = "CREDENCIAL_EMITIR";
    private static final String RECURSO = "CREDENCIAL_ACESSO";

    private final CredencialAcessoRepository credenciais;
    private final PedidoRepository pedidos;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public CredencialAcessoService(
            CredencialAcessoRepository credenciais, PedidoRepository pedidos,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria,
            OutboxService outbox) {
        this.credenciais = credenciais;
        this.pedidos = pedidos;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional
    public CredencialAcessoResponse emitir(
            Long organizacaoId, Long atorId, Long pedidoId,
            String chaveIdempotencia, EmitirCredencialRequest request) {
        String chave = validarChave(chaveIdempotencia);
        if (request == null || request.preferredMedium() == null) {
            throw new IllegalArgumentException("O meio preferido e obrigatorio.");
        }
        String requestHash = hash(pedidoId, request);
        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_EMITIR, chave);
        var anterior = idempotencia.buscar(
                organizacaoId, atorId, OPERACAO_EMITIR, chave);
        if (anterior.isPresent()) {
            if (!requestHash.equals(anterior.get().requestHash())) {
                throw new ConflitoException(
                        "A chave de idempotencia ja foi usada com outro payload.");
            }
            return CredencialAcessoResponse.from(
                    credenciais.findByIdAndOrganizacaoIdAndAtorId(
                                    anterior.get().recursoId(), organizacaoId, atorId)
                            .orElseThrow(() -> new ConflitoException(
                                    "O resultado idempotente nao esta mais disponivel.")));
        }

        Pedido pedido = pedidos.findDoAtorForUpdate(pedidoId, organizacaoId, atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Pedido", pedidoId));
        if (pedido.getStatus() != StatusPedido.CONFIRMADO) {
            throw new ConflitoException(
                    "Somente pedido confirmado e ainda nao atendido pode emitir credencial.");
        }
        long quantidadeEsperada = pedido.getItens().stream()
                .mapToLong(ItemPedido::getQuantidade).sum();
        String placaCredencial = quantidadeEsperada == 1
                ? pedido.getPlacaVeiculo() : null;
        if (request.preferredMedium() == MeioPreferidoCredencial.PLACA
                && placaCredencial == null) {
            throw new ConflitoException(
                    "A credencial por placa exige pedido unitario com placa valida.");
        }

        ItemPedido item = proximoItem(pedido);
        int numeroUnidade = Math.toIntExact(
                credenciais.countByItemPedidoId(item.getId()) + 1);
        CredencialAcesso credencial = CredencialAcesso.criar(
                organizacaoId, pedido.getEventoId(), item.getPatioId(), pedido.getId(),
                item.getId(), pedido.getAtorId(), codigoPublico(), numeroUnidade,
                OrigemCredencial.PEDIDO, item.getCategoria(), item.getDireito(),
                item.getInicioAcesso(), item.getFimAcesso(),
                request.preferredMedium(), placaCredencial);
        credencial.ativar();
        credencial = credenciais.saveAndFlush(credencial);

        Map<String, Object> estado = snapshot(credencial);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "CREDENCIAL_EMITIDA", RECURSO, credencial.getId(),
                credencial.getEventoId(), credencial.getPatioId(), null, null,
                estado, null, credencial.getVersion(),
                ClasseRetencaoAuditoria.OPERACIONAL));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "CredencialEmitida", organizacaoId, RECURSO, credencial.getId(),
                credencial.getVersion(), null, 1, estado));

        if (credenciais.countByPedidoId(pedido.getId()) == quantidadeEsperada) {
            Long versaoAnterior = pedido.getVersion();
            pedido.atender();
            pedidos.flush();
            Map<String, Object> pedidoAtendido = new LinkedHashMap<>();
            pedidoAtendido.put("pedidoId", pedido.getId());
            pedidoAtendido.put("eventoId", pedido.getEventoId());
            pedidoAtendido.put("status", pedido.getStatus().name());
            pedidoAtendido.put("quantidadeCredenciais", quantidadeEsperada);
            auditoria.registrar(new RegistrarAuditoriaCommand(
                    organizacaoId, "PEDIDO_ATENDIDO", "PEDIDO", pedido.getId(),
                    pedido.getEventoId(), credencial.getPatioId(), null,
                    Map.of("status", StatusPedido.CONFIRMADO.name()),
                    pedidoAtendido, versaoAnterior, pedido.getVersion(),
                    ClasseRetencaoAuditoria.OPERACIONAL));
            outbox.publicar(new PublicarEventoOutboxCommand(
                    "PedidoAtendido", organizacaoId, "PEDIDO", pedido.getId(),
                    pedido.getVersion(), null, 1, pedidoAtendido));
        }
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO_EMITIR, chave, requestHash,
                RECURSO, credencial.getId());
        return CredencialAcessoResponse.from(credencial);
    }

    @Transactional(readOnly = true)
    public CredencialAcessoResponse consultar(
            Long organizacaoId, Long atorId, Long credencialId) {
        return CredencialAcessoResponse.from(
                credenciais.findByIdAndOrganizacaoIdAndAtorId(
                                credencialId, organizacaoId, atorId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "Credencial", credencialId)));
    }

    private ItemPedido proximoItem(Pedido pedido) {
        return pedido.getItens().stream()
                .sorted(Comparator.comparing(ItemPedido::getId))
                .filter(item -> credenciais.countByItemPedidoId(item.getId())
                        < item.getQuantidade())
                .findFirst()
                .orElseThrow(() -> new ConflitoException(
                        "Todas as unidades do pedido ja possuem credencial."));
    }

    private static String codigoPublico() {
        return "CRD-" + UUID.randomUUID().toString().replace("-", "")
                .toUpperCase(Locale.ROOT);
    }

    private static String validarChave(String chave) {
        if (chave == null || chave.isBlank() || chave.trim().length() > 120) {
            throw new IllegalArgumentException(
                    "A chave de idempotencia deve ter entre 1 e 120 caracteres.");
        }
        return chave.trim();
    }

    private static String hash(Long pedidoId, EmitirCredencialRequest request) {
        String canonico = campo(pedidoId) + campo(request.preferredMedium().name());
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

    private static Map<String, Object> snapshot(CredencialAcesso credencial) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("credencialId", credencial.getId());
        estado.put("pedidoId", credencial.getPedidoId());
        estado.put("itemPedidoId", credencial.getItemPedidoId());
        estado.put("eventoId", credencial.getEventoId());
        estado.put("patioId", credencial.getPatioId());
        estado.put("codigoPublico", credencial.getCodigoPublico());
        estado.put("numeroUnidade", credencial.getNumeroUnidade());
        estado.put("direito", credencial.getDireito().name());
        estado.put("status", credencial.getStatus().name());
        estado.put("inicioValidade", credencial.getInicioValidade().toString());
        estado.put("fimValidade", credencial.getFimValidade().toString());
        return estado;
    }
}
