package com.kaneko.estacionamento.organizacao.venue.dto;

import com.kaneko.estacionamento.organizacao.venue.TipoUnidadeOperacional;
import com.kaneko.estacionamento.organizacao.venue.Venue;
import java.time.OffsetDateTime;

public record VenueResponse(
        Long id,
        Long organizationId,
        String name,
        TipoUnidadeOperacional type,
        String address,
        String number,
        String complement,
        String district,
        String postalCode,
        Long cityId,
        String timeZone,
        Boolean active,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getId(), venue.getOrganizacaoId(), venue.getNome(), venue.getTipo(),
                venue.getEndereco(), venue.getNumero(), venue.getComplemento(), venue.getBairro(),
                venue.getCep(), venue.getCidadeId(), venue.getFusoHorario(), venue.getAtivo(),
                venue.getVersion(), venue.getCreatedAt(), venue.getUpdatedAt());
    }
}
