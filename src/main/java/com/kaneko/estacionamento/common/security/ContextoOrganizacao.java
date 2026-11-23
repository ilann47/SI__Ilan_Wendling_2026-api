package com.kaneko.estacionamento.common.security;

public record ContextoOrganizacao(
        Long organizacaoId,
        Long membershipId,
        Long membershipVersion) {
}
