package com.kaneko.estacionamento.inventario.alocacao.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CriarAlocacaoPatioEventoRequest(
        @NotNull Long parkingFacilityId,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @NotNull @Min(1) Integer operationalCapacity,
        @NotNull @Min(0) Integer sellableCapacity,
        @NotNull @Min(0) Integer reservedCapacity) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
