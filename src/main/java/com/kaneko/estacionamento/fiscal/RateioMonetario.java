package com.kaneko.estacionamento.fiscal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Aloca unidades monetárias inteiras por maiores restos, com desempate estável. */
public final class RateioMonetario {
    private RateioMonetario() { }

    public static List<BigDecimal> distribuir(BigDecimal total, List<BigDecimal> pesos) {
        return distribuir(total, pesos, 2);
    }

    public static List<BigDecimal> distribuirEmMilionesimos(BigDecimal total, List<BigDecimal> pesos) {
        return distribuir(total, pesos, com.kaneko.estacionamento.common.util.MilionesimosConverter.ESCALA);
    }

    private static List<BigDecimal> distribuir(BigDecimal total, List<BigDecimal> pesos, int escalaValor) {
        BigInteger unidades = total.setScale(escalaValor, RoundingMode.UNNECESSARY).unscaledValue();
        if (unidades.signum() < 0 || pesos.stream().anyMatch(p -> p == null || p.signum() < 0))
            throw new IllegalArgumentException("Valores e pesos de rateio devem ser não negativos.");
        if (pesos.isEmpty()) {
            if (unidades.signum() != 0) throw new IllegalArgumentException("Não há parcelas para o rateio.");
            return List.of();
        }
        int escala = pesos.stream().mapToInt(BigDecimal::scale).max().orElse(0);
        List<BigInteger> inteiros = pesos.stream().map(p -> p.setScale(escala).unscaledValue()).toList();
        BigInteger base = inteiros.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (base.signum() == 0 && unidades.signum() != 0)
            throw new IllegalArgumentException("O rateio exige uma base positiva.");
        var valores = new ArrayList<BigInteger>();
        var restos = new ArrayList<BigInteger>();
        BigInteger alocado = BigInteger.ZERO;
        for (BigInteger peso : inteiros) {
            BigInteger[] divisao = base.signum() == 0 ? new BigInteger[]{BigInteger.ZERO, BigInteger.ZERO}
                    : unidades.multiply(peso).divideAndRemainder(base);
            valores.add(divisao[0]); restos.add(divisao[1]); alocado = alocado.add(divisao[0]);
        }
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < pesos.size(); i++) indices.add(i);
        indices.sort(Comparator.<Integer, BigInteger>comparing(restos::get).reversed().thenComparingInt(i -> i));
        int faltam = unidades.subtract(alocado).intValueExact();
        for (int i = 0; i < faltam; i++) {
            int indice = indices.get(i);
            valores.set(indice, valores.get(indice).add(BigInteger.ONE));
        }
        return valores.stream().map(v -> new BigDecimal(v, escalaValor)).toList();
    }
}
