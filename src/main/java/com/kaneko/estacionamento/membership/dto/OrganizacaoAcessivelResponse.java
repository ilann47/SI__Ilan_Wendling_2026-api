package com.kaneko.estacionamento.membership.dto;

public record OrganizacaoAcessivelResponse(
        Long organizationId,
        String legalName,
        String tradeName,
        Long membershipId,
        Long membershipVersion) {
}
