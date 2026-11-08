package com.kaneko.estacionamento.estoque;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Regra autoritativa do custo medio ponderado de uma entrada de estoque. */
public final class CustoMedioPonderado {
    private static final int ESCALA_MONETARIA = 2;

    private CustoMedioPonderado() {
    }

    public static BigDecimal calcular(BigDecimal saldoAtual, BigDecimal custoMedioAtual,
            BigDecimal quantidadeEntrada, BigDecimal custoUnitarioEntrada) {
        return calcular(saldoAtual, custoMedioAtual, quantidadeEntrada, custoUnitarioEntrada, ESCALA_MONETARIA);
    }

    public static BigDecimal calcularEmMilionesimos(BigDecimal saldoAtual, BigDecimal custoMedioAtual,
            BigDecimal quantidadeEntrada, BigDecimal custoUnitarioEntrada) {
        return calcular(saldoAtual, custoMedioAtual, quantidadeEntrada, custoUnitarioEntrada,
                com.kaneko.estacionamento.common.util.MilionesimosConverter.ESCALA);
    }

    private static BigDecimal calcular(BigDecimal saldoAtual, BigDecimal custoMedioAtual,
            BigDecimal quantidadeEntrada, BigDecimal custoUnitarioEntrada, int escala) {
        Objects.requireNonNull(saldoAtual, "saldoAtual");
        Objects.requireNonNull(custoMedioAtual, "custoMedioAtual");
        Objects.requireNonNull(quantidadeEntrada, "quantidadeEntrada");
        Objects.requireNonNull(custoUnitarioEntrada, "custoUnitarioEntrada");
        if (saldoAtual.signum() < 0 || custoMedioAtual.signum() < 0
                || quantidadeEntrada.signum() <= 0 || custoUnitarioEntrada.signum() < 0) {
            throw new IllegalArgumentException("Saldo, custos e quantidade devem ser validos para calcular o custo medio.");
        }
        if (saldoAtual.signum() == 0) {
            return custoUnitarioEntrada.setScale(escala, RoundingMode.HALF_UP);
        }
        BigDecimal valorAnterior = saldoAtual.multiply(custoMedioAtual);
        BigDecimal valorEntrada = quantidadeEntrada.multiply(custoUnitarioEntrada);
        return valorAnterior.add(valorEntrada)
                .divide(saldoAtual.add(quantidadeEntrada), escala, RoundingMode.HALF_UP);
    }
}
