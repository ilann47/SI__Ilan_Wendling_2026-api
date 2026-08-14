package com.kaneko.estacionamento.vendas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ItemVendaAdministrativaRequest(
        @NotNull Long produtoId,
        @NotNull @Positive BigDecimal quantidade,
        @NotNull @PositiveOrZero BigDecimal valorUnitario,
        @PositiveOrZero BigDecimal valorDesconto) {
}
