package com.kaneko.estacionamento.acesso.dto;

import com.kaneko.estacionamento.acesso.Presenca;
import com.kaneko.estacionamento.acesso.SessaoEstacionamento;
import com.kaneko.estacionamento.acesso.TentativaAcesso;
import com.kaneko.estacionamento.credencial.CredencialAcesso;
import java.time.OffsetDateTime;

public record CheckoutResponse(
        Long accessAttemptId,
        String decision,
        String reasonCode,
        Long credentialId,
        Long presenceId,
        Long parkingSessionId,
        Integer resultingOccupancy,
        String credentialStatus,
        OffsetDateTime decidedAt,
        boolean barrierCommandRequested) {

    public static CheckoutResponse from(
            TentativaAcesso tentativa, Presenca presenca,
            SessaoEstacionamento sessao, CredencialAcesso credencial) {
        return fromStatus(tentativa, presenca, sessao,
                credencial == null ? null : credencial.getStatus().name());
    }

    public static CheckoutResponse fromStatus(
            TentativaAcesso tentativa, Presenca presenca,
            SessaoEstacionamento sessao, String credentialStatus) {
        return new CheckoutResponse(
                tentativa.getId(), tentativa.getStatus().name(),
                tentativa.getMotivo().name(), tentativa.getCredencialId(),
                presenca == null ? null : presenca.getId(),
                sessao == null ? null : sessao.getId(),
                tentativa.getOcupacaoResultante(),
                credentialStatus,
                tentativa.getDecididoEm(), false);
    }
}
