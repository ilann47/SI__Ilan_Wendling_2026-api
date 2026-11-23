package com.kaneko.estacionamento.inventario.alocacao.dto;

import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEvento;
import java.time.OffsetDateTime;

public record AlocacaoPatioEventoResponse(
        Long id,
        Long organizationId,
        Long eventId,
        Long parkingFacilityId,
        String parkingFacilityName,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Integer operationalCapacity,
        Integer sellableCapacity,
        Integer reservedCapacity,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AlocacaoPatioEventoResponse from(AlocacaoPatioEvento alocacao, String patioNome) {
        return new AlocacaoPatioEventoResponse(
                alocacao.getId(), alocacao.getOrganizacaoId(), alocacao.getEventoId(),
                alocacao.getPatioId(), patioNome, alocacao.getInicio(), alocacao.getFim(),
                alocacao.getCapacidadeOperacional(), alocacao.getCapacidadeVendavel(),
                alocacao.getCapacidadeReservada(), alocacao.getVersion(),
                alocacao.getCreatedAt(), alocacao.getUpdatedAt());
    }
}
