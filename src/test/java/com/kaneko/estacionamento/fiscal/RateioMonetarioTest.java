package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RateioMonetarioTest {
    @Test void conservaCadaMilionesimoComDesempateEstavel() {
        var valores = RateioMonetario.distribuirEmMilionesimos(new BigDecimal("1.000001"),
                List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThat(valores).containsExactly(new BigDecimal("0.333334"),
                new BigDecimal("0.333334"), new BigDecimal("0.333333"));
    }
    @Test void muitosItensNaoCriamResidualNegativo() {
        var pesos = IntStream.range(0, 10).mapToObj(i -> BigDecimal.ONE).toList();
        assertThat(RateioMonetario.distribuir(new BigDecimal("0.05"), pesos))
                .containsExactly(new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.01"),
                        new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.00"),
                        new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    @Test void conservaCentavosEmPesosFracionariosEBaseQuantitativa() {
        for (int centavos = 0; centavos <= 50; centavos++) {
            BigDecimal total = BigDecimal.valueOf(centavos, 2);
            var valores = RateioMonetario.distribuir(total,
                    List.of(new BigDecimal("0.123"), new BigDecimal("0.000"), new BigDecimal("0.456"), new BigDecimal("0.111")));
            assertThat(valores).allMatch(v -> v.signum() >= 0 && v.scale() == 2);
            assertThat(valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
            assertThat(valores.get(1)).isEqualByComparingTo("0");
        }
    }

    @Test void desempateEstavelDistribuiParcelasSemObrigacaoNegativa() {
        assertThat(RateioMonetario.distribuir(new BigDecimal("0.02"),
                List.of(new BigDecimal("25"), new BigDecimal("25"), new BigDecimal("25"), new BigDecimal("25"))))
                .containsExactly(new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    @Test void rejeitaPerdaDePrecisaoEPesosInvalidos() {
        assertThatThrownBy(() -> RateioMonetario.distribuir(new BigDecimal("0.001"), List.of(BigDecimal.ONE)))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> RateioMonetario.distribuir(BigDecimal.ONE, List.of(BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RateioMonetario.distribuir(BigDecimal.ONE, List.of(BigDecimal.ONE.negate())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
