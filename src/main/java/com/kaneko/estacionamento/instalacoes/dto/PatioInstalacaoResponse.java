package com.kaneko.estacionamento.instalacoes.dto;

import com.kaneko.estacionamento.instalacoes.PatioInstalacao;
import java.time.OffsetDateTime;

public record PatioInstalacaoResponse(
        Long id,
        Long organizationId,
        String name,
        String address,
        String number,
        String complement,
        String district,
        String postalCode,
        Long cityId,
        String cityName,
        Integer physicalCapacity,
        Integer operationalCapacity,
        String notes,
        Boolean active,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static PatioInstalacaoResponse from(PatioInstalacao patio, String cityName) {
        return new PatioInstalacaoResponse(
                patio.getId(), patio.getOrganizacaoId(), patio.getNome(), patio.getEndereco(),
                patio.getNumero(), patio.getComplemento(), patio.getBairro(), patio.getCep(),
                patio.getCidadeId(), cityName, patio.getCapacidadeFisica(), patio.getCapacidadeOperacional(),
                patio.getObservacao(), patio.getAtivo(), patio.getVersion(), patio.getCreatedAt(),
                patio.getUpdatedAt());
    }
}
