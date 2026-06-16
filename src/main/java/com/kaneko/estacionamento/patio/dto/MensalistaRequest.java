package com.kaneko.estacionamento.patio.dto;

import com.kaneko.estacionamento.patio.StatusMensalista;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de entrada para criacao ou alteracao de um contrato de Mensalista.
 * Quando nao informados, {@code dataInicio} assume a data atual e
 * {@code status} assume ATIVO.
 */
public record MensalistaRequest(

        @NotNull(message = "O cliente é obrigatório.")
        Long clienteId,

        @NotNull(message = "O veículo é obrigatório.")
        Long veiculoId,

        @NotNull(message = "O valor mensal é obrigatório.")
        @PositiveOrZero(message = "O valor mensal não pode ser negativo.")
        BigDecimal valorMensal,

        LocalDate dataInicio,

        LocalDate dataFim,

        StatusMensalista status
) {
}
