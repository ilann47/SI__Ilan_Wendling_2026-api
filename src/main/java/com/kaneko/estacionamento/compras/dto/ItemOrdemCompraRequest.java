package com.kaneko.estacionamento.compras.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ItemOrdemCompraRequest(
        @NotNull Long produtoId,
        @NotNull @Positive BigDecimal quantidade,
        @NotNull @PositiveOrZero BigDecimal valorUnitario,
        @PositiveOrZero BigDecimal valorDesconto) {
}
