package com.kaneko.estacionamento.fiscal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Item informado na criacao de uma nota de entrada.
 */
public record ItemNotaEntradaRequest(

        @NotNull(message = "O produto do item é obrigatório.")
        Long produtoId,

        @NotNull(message = "A quantidade é obrigatória.")
        @Positive(message = "A quantidade deve ser maior que zero.")
        BigDecimal quantidade,

        @NotNull(message = "O valor unitário é obrigatório.")
        @PositiveOrZero(message = "O valor unitário não pode ser negativo.")
        BigDecimal valorUnitario,

        @PositiveOrZero(message = "O desconto do item não pode ser negativo.")
        BigDecimal valorDesconto,

        @PositiveOrZero @DecimalMax(value = "100.00", message = "O percentual de desconto não pode exceder 100.")
        BigDecimal percentualDesconto
) {
}
