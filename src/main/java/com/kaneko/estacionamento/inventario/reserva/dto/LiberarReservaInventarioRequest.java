package com.kaneko.estacionamento.inventario.reserva.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LiberarReservaInventarioRequest(
        @NotBlank @Size(max = 300)
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,299}") String reason) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
