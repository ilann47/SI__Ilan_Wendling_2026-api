package com.kaneko.estacionamento.patio;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PatioCobrancaCentavosTest {
    @Test void fechaCentavosHalfUpMantendoBaseEDiferencaComSeisCasas() {
        var abaixo = PatioCobrancaCentavos.paraCobrar(new BigDecimal("25.124999"));
        assertThat(abaixo.valorCalculado()).isEqualByComparingTo("25.124999");
        assertThat(abaixo.valorDevido()).isEqualByComparingTo("25.120000");
        assertThat(abaixo.diferencaArredondamento()).isEqualByComparingTo("-0.004999");
        var empate = PatioCobrancaCentavos.paraCobrar(new BigDecimal("25.125000"));
        assertThat(empate.valorDevido()).isEqualByComparingTo("25.130000");
        assertThat(empate.diferencaArredondamento()).isEqualByComparingTo("0.005000");
        assertThat(empate.valorDevido().scale()).isEqualTo(6);
    }
    @Test void naoTransformaPrecoPositivoEmSaidaSemCobranca() {
        assertThatThrownBy(() -> PatioCobrancaCentavos.paraCobrar(new BigDecimal("0.004999")))
            .hasMessageContaining("centavo");
        assertThat(PatioCobrancaCentavos.paraCobrar(BigDecimal.ZERO).valorDevido()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> PatioCobrancaCentavos.paraCobrar(new BigDecimal("-1"))).hasMessageContaining("negativo");
    }
}
