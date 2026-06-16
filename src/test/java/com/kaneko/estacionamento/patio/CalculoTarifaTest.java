package com.kaneko.estacionamento.patio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Testes unitarios (puros) da regra de cobranca avulsa. Tarifa de referencia:
 * R$ 5,00/hora, teto diaria R$ 30,00, tolerancia 15 minutos.
 */
class CalculoTarifaTest {

    private static final BigDecimal VALOR_HORA = new BigDecimal("5.00");
    private static final BigDecimal VALOR_DIARIA = new BigDecimal("30.00");
    private static final int TOLERANCIA = 15;
    private static final OffsetDateTime ENTRADA = OffsetDateTime.parse("2026-01-01T08:00:00-03:00");

    private BigDecimal cobrar(long minutos) {
        return CalculoTarifa.calcular(VALOR_HORA, VALOR_DIARIA, TOLERANCIA, ENTRADA, ENTRADA.plusMinutes(minutos));
    }

    @Test
    void cortesiaDentroDaTolerancia() {
        assertEquals(new BigDecimal("0.00"), cobrar(10));
        assertEquals(new BigDecimal("0.00"), cobrar(15)); // limite exato ainda e cortesia
    }

    @Test
    void primeiraHoraAoUltrapassarTolerancia() {
        assertEquals(new BigDecimal("5.00"), cobrar(16)); // 16 min -> 1 hora cheia
        assertEquals(new BigDecimal("5.00"), cobrar(60)); // 60 min -> 1 hora
    }

    @Test
    void fracaoDeHoraArredondaParaCima() {
        assertEquals(new BigDecimal("10.00"), cobrar(61)); // 61 min -> 2 horas
        assertEquals(new BigDecimal("10.00"), cobrar(90)); // 90 min -> 2 horas
    }

    @Test
    void tetoDaDiariaLimitaOValor() {
        // 10 horas = 600 min -> 10*5 = 50, mas teto de 1 diaria = 30
        assertEquals(new BigDecimal("30.00"), cobrar(600));
    }

    @Test
    void multiplasDiariasSomamOTeto() {
        // 30 horas = 1800 min -> 30*5 = 150, mas teto de 2 diarias = 60
        assertEquals(new BigDecimal("60.00"), cobrar(30 * 60));
    }
}
