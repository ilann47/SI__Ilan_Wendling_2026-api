package com.kaneko.estacionamento.evento.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CriarEventoRequest(
        @NotBlank @Size(max = 180) String name,
        @NotNull Long venueId,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @NotBlank @Size(max = 60) String timeZone,
        @Size(max = 160) String externalId) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
