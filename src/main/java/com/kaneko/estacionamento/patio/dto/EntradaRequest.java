package com.kaneko.estacionamento.patio.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Dados de entrada para registrar a chegada de um veiculo ao patio. O tipo
 * (avulso ou mensalista) e determinado automaticamente conforme o veiculo
 * possua, ou nao, um contrato de mensalista ativo.
 */
public record EntradaRequest(

        @NotNull(message = "O veículo é obrigatório.")
        Long veiculoId
) {
}
