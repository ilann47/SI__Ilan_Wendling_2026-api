package com.kaneko.estacionamento.patio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dados de entrada para criacao ou alteracao de uma Tarifa. Os valores devem
 * ser nao-negativos (espelha {@code ck_tarifa_valores}). Quando
 * {@code toleranciaMin} nao e informado, assume-se o padrao de 15 minutos.
 */
public record TarifaRequest(

        @NotBlank(message = "A descrição é obrigatória.")
        @Size(max = 80, message = "A descrição deve ter no máximo 80 caracteres.")
        String descricao,

        @NotNull(message = "O valor por hora é obrigatório.")
        @PositiveOrZero(message = "O valor por hora não pode ser negativo.")
        BigDecimal valorHora,

        @NotNull(message = "O valor da diária é obrigatório.")
        @PositiveOrZero(message = "O valor da diária não pode ser negativo.")
        BigDecimal valorDiaria,

        @PositiveOrZero(message = "A tolerância não pode ser negativa.")
        Integer toleranciaMin,

        @NotNull(message = "O valor da mensalidade é obrigatório.")
        @PositiveOrZero(message = "O valor da mensalidade não pode ser negativo.")
        BigDecimal valorMensalidade,

        Boolean ativo
) {
}
