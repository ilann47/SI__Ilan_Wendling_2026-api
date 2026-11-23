package com.kaneko.estacionamento.fiscal.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ItemNotaServicoRequest(
        @NotNull @Positive Long servicoId,
        @NotNull @Positive @Digits(integer = 9, fraction = 6) BigDecimal quantidade,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal valorUnitario,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal valorDesconto) {
}
