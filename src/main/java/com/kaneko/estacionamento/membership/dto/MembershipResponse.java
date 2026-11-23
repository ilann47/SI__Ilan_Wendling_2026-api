package com.kaneko.estacionamento.membership.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kaneko.estacionamento.membership.MembroOrganizacao;
import com.kaneko.estacionamento.membership.OrigemMembroOrganizacao;
import com.kaneko.estacionamento.membership.StatusMembroOrganizacao;
import java.time.OffsetDateTime;

public record MembershipResponse(
        Long id,
        @JsonProperty("organizationId") Long organizacaoId,
        @JsonProperty("userId") Long usuarioId,
        StatusMembroOrganizacao status,
        @JsonProperty("origin") OrigemMembroOrganizacao origem,
        @JsonProperty("joinedAt") OffsetDateTime entrouEm,
        @JsonProperty("suspendedAt") OffsetDateTime suspensoEm,
        @JsonProperty("endedAt") OffsetDateTime encerradoEm,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static MembershipResponse from(MembroOrganizacao membro) {
        return new MembershipResponse(
                membro.getId(), membro.getOrganizacaoId(), membro.getUsuarioId(),
                membro.getStatus(), membro.getOrigem(), membro.getEntrouEm(),
                membro.getSuspensoEm(), membro.getEncerradoEm(), membro.getVersion(),
                membro.getCreatedAt(), membro.getUpdatedAt());
    }
}
