package com.kaneko.estacionamento.compras.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ItemRecebimentoCompraRequest(
        @NotNull Long itemOrdemCompraId,
        @NotNull @Positive BigDecimal quantidade) {
}
