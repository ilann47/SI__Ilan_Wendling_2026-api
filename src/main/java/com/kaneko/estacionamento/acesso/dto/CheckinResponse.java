package com.kaneko.estacionamento.acesso.dto;

import com.kaneko.estacionamento.acesso.Presenca;
import com.kaneko.estacionamento.acesso.SessaoEstacionamento;
import com.kaneko.estacionamento.acesso.TentativaAcesso;
import java.time.OffsetDateTime;

public record CheckinResponse(
        Long accessAttemptId,
        String decision,
        String reasonCode,
        Long credentialId,
        Long presenceId,
        Long parkingSessionId,
        Integer resultingOccupancy,
        OffsetDateTime decidedAt,
        boolean barrierCommandRequested) {

    public static CheckinResponse from(
            TentativaAcesso tentativa, Presenca presenca,
            SessaoEstacionamento sessao) {
        return new CheckinResponse(
                tentativa.getId(), tentativa.getStatus().name(),
                tentativa.getMotivo().name(), tentativa.getCredencialId(),
                presenca == null ? null : presenca.getId(),
                sessao == null ? null : sessao.getId(),
                tentativa.getOcupacaoResultante(), tentativa.getDecididoEm(),
                false);
    }
}
