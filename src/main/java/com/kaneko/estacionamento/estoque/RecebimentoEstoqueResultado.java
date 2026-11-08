package com.kaneko.estacionamento.estoque;

import java.math.BigDecimal;

/** Resultado financeiro e quantitativo efetivamente aplicado ao estoque. */
public record RecebimentoEstoqueResultado(
        Long movimentoId,
        BigDecimal saldoTotalAnterior,
        BigDecimal saldoTotalPosterior,
        BigDecimal custoMedioAnterior,
        BigDecimal custoMedioPosterior
) {
}
