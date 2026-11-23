package com.kaneko.estacionamento.estoque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CustoMedioPonderadoTest {

    @Test
    void primeiraEntradaAssumeOCustoUnitarioDaEntrada() {
        assertThat(CustoMedioPonderado.calcular(
                BigDecimal.ZERO, new BigDecimal("99.99"),
                new BigDecimal("5"), new BigDecimal("12.34")))
                .isEqualByComparingTo("12.34");
    }

    @Test
    void ponderaSaldoAnteriorECustoDaNovaEntrada() {
        assertThat(CustoMedioPonderado.calcular(
                new BigDecimal("10"), new BigDecimal("20"),
                new BigDecimal("5"), new BigDecimal("30")))
                .isEqualByComparingTo("23.33");
    }

    @Test
    void consideraQuantidadesFracionadasEArredondaSomenteNoResultado() {
        assertThat(CustoMedioPonderado.calcular(
                new BigDecimal("1.250"), new BigDecimal("7.15"),
                new BigDecimal("0.750"), new BigDecimal("9.80")))
                .isEqualByComparingTo("8.14");
    }

    @Test
    void recusaEntradaSemQuantidadeOuComValoresNegativos() {
        assertThatThrownBy(() -> CustoMedioPonderado.calcular(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CustoMedioPonderado.calcular(
                BigDecimal.ONE.negate(), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
