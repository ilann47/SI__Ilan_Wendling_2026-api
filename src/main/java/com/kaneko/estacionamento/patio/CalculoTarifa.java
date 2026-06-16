package com.kaneko.estacionamento.patio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Calculo da cobranca avulsa do patio.
 *
 * <p>Regra (espelha o caso de uso da operacao de patio):
 * <ul>
 *   <li>Permanencia menor ou igual a {@code toleranciaMin} (cortesia): gratis.</li>
 *   <li>Caso contrario, cobra-se por hora cheia (fracao arredonda para cima)
 *       ao {@code valorHora}.</li>
 *   <li>Teto: o valor nunca excede o numero de diarias (dias corridos
 *       arredondados para cima) multiplicado por {@code valorDiaria}.</li>
 * </ul>
 *
 * <p>Classe utilitaria pura (sem dependencia de Spring), para permitir teste
 * isolado da regra de cobranca.
 */
public final class CalculoTarifa {

    private static final long MINUTOS_POR_HORA = 60L;
    private static final long MINUTOS_POR_DIA = 24L * 60L;

    private CalculoTarifa() {
    }

    /**
     * Calcula o valor a cobrar de uma permanencia avulsa entre {@code entrada}
     * e {@code saida}, com escala monetaria de 2 casas.
     */
    public static BigDecimal calcular(BigDecimal valorHora,
                                      BigDecimal valorDiaria,
                                      int toleranciaMin,
                                      OffsetDateTime entrada,
                                      OffsetDateTime saida) {
        long minutos = Duration.between(entrada, saida).toMinutes();

        if (minutos <= toleranciaMin) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        long horas = teto(minutos, MINUTOS_POR_HORA);
        long diarias = teto(minutos, MINUTOS_POR_DIA);

        BigDecimal porHora = valorHora.multiply(BigDecimal.valueOf(horas));
        BigDecimal tetoDiarias = valorDiaria.multiply(BigDecimal.valueOf(diarias));

        return porHora.min(tetoDiarias).setScale(2, RoundingMode.HALF_UP);
    }

    /** Divisao inteira arredondada para cima (ceil). */
    private static long teto(long valor, long divisor) {
        return (valor + divisor - 1) / divisor;
    }
}
