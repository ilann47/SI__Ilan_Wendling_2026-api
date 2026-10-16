package com.kaneko.estacionamento.evento.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.evento.PoliticaReentrada;
import jakarta.validation.constraints.NotNull;

public record AtualizarEventoRequest(
        @NotNull PoliticaReentrada reentryPolicy) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
