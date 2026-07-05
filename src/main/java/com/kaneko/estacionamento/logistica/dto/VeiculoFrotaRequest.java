package com.kaneko.estacionamento.logistica.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dados de entrada para criacao ou alteracao de um Veiculo de Frota.
 */
public record VeiculoFrotaRequest(

        @NotBlank(message = "A placa é obrigatória.")
        @Size(max = 10, message = "A placa deve ter no máximo 10 caracteres.")
        String placa,

        @Size(max = 50) String modelo,
        @Size(max = 50) String marca,

        @Min(value = 1900, message = "O ano deve ser a partir de 1900.")
        Integer ano,

        @PositiveOrZero(message = "A capacidade não pode ser negativa.")
        BigDecimal capacidade,

        @Size(max = 255) String observacao,

        Boolean ativo
) {
}
