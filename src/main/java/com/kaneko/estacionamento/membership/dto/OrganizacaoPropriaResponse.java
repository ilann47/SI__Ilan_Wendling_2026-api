package com.kaneko.estacionamento.membership.dto;

import com.kaneko.estacionamento.organizacao.dto.OrganizacaoResponse;

/** Resultado atomico do bootstrap de organizacao e vinculo administrativo inicial. */
public record OrganizacaoPropriaResponse(
        OrganizacaoResponse organization,
        MembershipResponse membership) {
}
