package com.kaneko.estacionamento.credencial.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.credencial.MeioPreferidoCredencial;
import jakarta.validation.constraints.NotNull;

public record EmitirCredencialRequest(
        @NotNull MeioPreferidoCredencial preferredMedium) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
