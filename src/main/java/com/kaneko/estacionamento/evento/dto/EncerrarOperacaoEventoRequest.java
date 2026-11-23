package com.kaneko.estacionamento.evento.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record EncerrarOperacaoEventoRequest(
        @NotNull @AssertTrue Boolean confirmed,
        @NotNull @AssertTrue Boolean financialPendingAcknowledged) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
