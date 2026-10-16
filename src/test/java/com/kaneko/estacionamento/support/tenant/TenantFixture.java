package com.kaneko.estacionamento.support.tenant;

public record TenantFixture(
        Long organizacaoId,
        Long usuarioId,
        Long membershipId,
        String tokenGlobal,
        String tokenContextual) {
}
