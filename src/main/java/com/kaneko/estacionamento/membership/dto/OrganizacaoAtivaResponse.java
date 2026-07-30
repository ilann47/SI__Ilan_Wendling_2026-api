package com.kaneko.estacionamento.membership.dto;

import com.kaneko.estacionamento.common.security.ContextoOrganizacao;

public record OrganizacaoAtivaResponse(
        Long organizationId,
        Long membershipId,
        Long membershipVersion) {

    public static OrganizacaoAtivaResponse from(ContextoOrganizacao contexto) {
        return new OrganizacaoAtivaResponse(
                contexto.organizacaoId(), contexto.membershipId(), contexto.membershipVersion());
    }
}
