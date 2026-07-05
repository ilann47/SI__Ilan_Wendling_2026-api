package com.kaneko.estacionamento.logistica.dto;

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
}
