package com.kaneko.estacionamento.membership.dto;

import com.kaneko.estacionamento.common.security.ContextoOrganizacao;
import java.time.OffsetDateTime;

public record TokenContextoResponse(
        String token,
        String type,
        OffsetDateTime expiresAt,
        OrganizacaoAtivaResponse activeOrganization) {

    public static TokenContextoResponse of(
            String token, OffsetDateTime expiresAt, ContextoOrganizacao contexto) {
        return new TokenContextoResponse(
                token, "Bearer", expiresAt, OrganizacaoAtivaResponse.from(contexto));
    }
}
