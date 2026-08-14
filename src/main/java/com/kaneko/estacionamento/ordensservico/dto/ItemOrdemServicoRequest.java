package com.kaneko.estacionamento.ordensservico.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ItemOrdemServicoRequest(
        @NotNull Long servicoId,
        @NotNull @Positive BigDecimal quantidade,
        @NotNull @PositiveOrZero BigDecimal valorUnitario,
        @PositiveOrZero BigDecimal valorDesconto) {
}
