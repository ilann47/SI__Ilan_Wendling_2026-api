package com.kaneko.estacionamento.financeiro;

import static org.assertj.core.api.Assertions.*;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FinanceiroRegrasTest {
    @Test void totalNaoPodeEsconderDivergenciaDosComponentes() {
        assertThatThrownBy(() -> FinanceiroRegras.total(new BigDecimal("100"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100")))
                .isInstanceOf(ConflitoException.class);
    }
    @Test void calculaTotalComDescontoEAcrescimos() {
        assertThat(FinanceiroRegras.total(new BigDecimal("100"), new BigDecimal("10"),
                new BigDecimal("2"), new BigDecimal("3"), null)).isEqualByComparingTo("95");
    }
    @Test void baixaRejeitaValorZeroNegativoEFracaoDeCentavo() {
        for (String valor : new String[]{"0", "-1", "0.001"}) {
            assertThatThrownBy(() -> FinanceiroRegras.validarBaixa(new BigDecimal(valor)))
                    .isInstanceOf(ConflitoException.class);
        }
    }
    @Test void rejeitaDescontoMaiorQueOriginal() {
        assertThatThrownBy(() -> FinanceiroRegras.total(new BigDecimal("10"), new BigDecimal("20"),
                BigDecimal.ZERO, BigDecimal.ZERO, null)).isInstanceOf(ConflitoException.class);
    }
}
