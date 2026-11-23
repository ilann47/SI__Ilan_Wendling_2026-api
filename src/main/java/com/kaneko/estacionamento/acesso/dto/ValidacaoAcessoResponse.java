package com.kaneko.estacionamento.acesso.dto;

import com.kaneko.estacionamento.acesso.TentativaAcesso;
import java.time.OffsetDateTime;

public record ValidacaoAcessoResponse(
        Long accessAttemptId,
        String decision,
        String reasonCode,
        Long credentialId,
        OffsetDateTime decidedAt,
        boolean consumptionPerformed) {

    public static ValidacaoAcessoResponse from(TentativaAcesso tentativa) {
        return new ValidacaoAcessoResponse(
                tentativa.getId(), tentativa.getStatus().name(),
                tentativa.getMotivo().name(), tentativa.getCredencialId(),
                tentativa.getDecididoEm(), false);
    }
}
