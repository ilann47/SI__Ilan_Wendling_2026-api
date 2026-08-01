package com.kaneko.estacionamento.acesso.dto;

import com.kaneko.estacionamento.acesso.MotivoDecisaoAcesso;
import com.kaneko.estacionamento.acesso.StatusTentativaAcesso;
import com.kaneko.estacionamento.acesso.TentativaAcesso;
import java.time.OffsetDateTime;

public record TentativaAcessoResponse(
        Long id,
        Long eventId,
        Long parkingFacilityId,
        Long actorId,
        Long credentialId,
        String direction,
        String readSource,
        String lane,
        StatusTentativaAcesso decision,
        MotivoDecisaoAcesso reasonCode,
        Integer resultingOccupancy,
        OffsetDateTime receivedAt,
        OffsetDateTime decidedAt) {

    public static TentativaAcessoResponse from(TentativaAcesso tentativa) {
        return new TentativaAcessoResponse(
                tentativa.getId(), tentativa.getEventoId(),
                tentativa.getPatioId(), tentativa.getAtorId(),
                tentativa.getCredencialId(), tentativa.getDirecao(),
                tentativa.getOrigemLeitura(), tentativa.getFaixa(),
                tentativa.getStatus(), tentativa.getMotivo(),
                tentativa.getOcupacaoResultante(), tentativa.getRecebidoEm(),
                tentativa.getDecididoEm());
    }
}
