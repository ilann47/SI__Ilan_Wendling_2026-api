package com.kaneko.estacionamento.inventario.reserva.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriarReservaInventarioRequest(
        @NotNull Long parkingProductId,
        @NotNull @Min(1) Integer quantity,
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,29}") String channel,
        @Size(max = 160) String buyerReference) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
