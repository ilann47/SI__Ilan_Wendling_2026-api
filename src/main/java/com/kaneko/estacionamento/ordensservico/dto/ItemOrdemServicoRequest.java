package com.kaneko.estacionamento.ordensservico.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ItemOrdemServicoRequest(
        @NotNull Long servicoId,
        @NotNull @Positive @Digits(integer = 12, fraction = 6) BigDecimal quantidade,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal valorUnitario,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal valorDesconto) {
}
