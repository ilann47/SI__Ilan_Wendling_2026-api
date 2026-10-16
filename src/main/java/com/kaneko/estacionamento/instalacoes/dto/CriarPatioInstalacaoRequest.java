package com.kaneko.estacionamento.instalacoes.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarPatioInstalacaoRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String address,
        @Size(max = 10) String number,
        @Size(max = 100) String complement,
        @NotBlank @Size(max = 60) String district,
        @NotBlank @Size(max = 9) String postalCode,
        @NotNull Long cityId,
        @NotNull @Min(1) Integer physicalCapacity,
        @NotNull @Min(0) Integer operationalCapacity,
        @Size(max = 255) String notes,
        Boolean active) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
