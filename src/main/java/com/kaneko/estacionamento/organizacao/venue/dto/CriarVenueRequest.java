package com.kaneko.estacionamento.organizacao.venue.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarVenueRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 120) String address,
        @Size(max = 10) String number,
        @Size(max = 100) String complement,
        @NotBlank @Size(max = 60) String district,
        @NotBlank @Size(max = 9) String postalCode,
        @NotNull Long cityId,
        @NotBlank @Size(max = 60) String timeZone) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
