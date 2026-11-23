package com.kaneko.estacionamento.logistica.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

/**
 * Dados de entrada do vinculo transportadora-veiculo (frota).
 */
public record TransportadoraVeiculoFrotaRequest(

        @NotNull(message = "A transportadora é obrigatória.")
        Long transportadoraId,

        @NotNull(message = "O veículo é obrigatório.")
        Long veiculoFrotaId
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
