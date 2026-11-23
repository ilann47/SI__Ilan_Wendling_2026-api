package com.kaneko.estacionamento.credencial;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.pedido.CredenciaisPedidoPort;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CredenciaisPedidoAdapter implements CredenciaisPedidoPort {
    private final CredencialAcessoRepository credenciais;
    private final RepresentacaoQrRepository representacoesQr;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public CredenciaisPedidoAdapter(
            CredencialAcessoRepository credenciais,
            RepresentacaoQrRepository representacoesQr,
            AuditoriaService auditoria, OutboxService outbox) {
        this.credenciais = credenciais;
        this.representacoesQr = representacoesQr;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int bloquearDoPedido(
            Long organizacaoId, Long pedidoId, String motivo,
            OffsetDateTime instante) {
        int bloqueadas = 0;
        for (CredencialAcesso credencial : credenciais.findDoPedidoForUpdate(
                pedidoId, organizacaoId)) {
            if (credencial.getStatus() == StatusCredencial.BLOQUEADA) {
                continue;
            }
            if (credencial.getStatus() != StatusCredencial.ATIVA) {
                throw new ConflitoException(
                        "Credencial usada ou terminal impede o cancelamento do pedido.");
            }
            Long versaoAnterior = credencial.getVersion();
            Map<String, Object> anterior = snapshot(credencial);
            credencial.bloquear(motivo, instante);
            representacoesQr.findAtivaForUpdate(credencial.getId())
                    .ifPresent(qr -> qr.revogar(instante));
            credenciais.flush();
            representacoesQr.flush();
            Map<String, Object> posterior = snapshot(credencial);
            auditoria.registrar(new RegistrarAuditoriaCommand(
                    organizacaoId, "CREDENCIAL_BLOQUEADA", "CREDENCIAL_ACESSO",
                    credencial.getId(), credencial.getEventoId(),
                    credencial.getPatioId(), motivo, anterior, posterior,
                    versaoAnterior, credencial.getVersion(),
                    ClasseRetencaoAuditoria.OPERACIONAL));
            outbox.publicar(new PublicarEventoOutboxCommand(
                    "CredencialBloqueada", organizacaoId, "CREDENCIAL_ACESSO",
                    credencial.getId(), credencial.getVersion(), null, 1,
                    posterior));
            bloqueadas++;
        }
        return bloqueadas;
    }

    private static Map<String, Object> snapshot(CredencialAcesso credencial) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("credencialId", credencial.getId());
        estado.put("pedidoId", credencial.getPedidoId());
        estado.put("status", credencial.getStatus().name());
        estado.put("qrVersao", credencial.getQrVersao());
        if (credencial.getBloqueadaEm() != null) {
            estado.put("bloqueadaEm", credencial.getBloqueadaEm().toString());
            estado.put("motivoBloqueio", credencial.getMotivoBloqueio());
        }
        return estado;
    }
}
