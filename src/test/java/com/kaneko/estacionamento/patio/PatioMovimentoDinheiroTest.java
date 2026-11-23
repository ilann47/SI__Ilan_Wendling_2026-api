package com.kaneko.estacionamento.patio;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PatioMovimentoDinheiroTest {
    @Test void sangriaTemLimiteExatoSemUsarSaldoPixOuCartao(){
        var saldo=new BigDecimal("20.000001");
        assertThatCode(()->PatioMovimentoDinheiroService.exigirSaldo(PatioMovimentoDinheiroService.Tipo.SANGRIA,saldo,saldo)).doesNotThrowAnyException();
        assertThatThrownBy(()->PatioMovimentoDinheiroService.exigirSaldo(PatioMovimentoDinheiroService.Tipo.SANGRIA,new BigDecimal("20.000002"),saldo))
            .hasMessageContaining("disponível");
    }
    @Test void suprimentoNaoDependeDeSaldoENaoAceitaZeroNegativoOuSetimaCasa(){
        assertThatCode(()->PatioMovimentoDinheiroService.exigirSaldo(PatioMovimentoDinheiroService.Tipo.SUPRIMENTO,BigDecimal.ONE,BigDecimal.ZERO)).doesNotThrowAnyException();
        for(var invalid:new String[]{"0","-1","0.0000001"})
            assertThatThrownBy(()->PatioMovimentoDinheiroService.exigirSaldo(PatioMovimentoDinheiroService.Tipo.SUPRIMENTO,new BigDecimal(invalid),BigDecimal.ZERO))
                .isInstanceOf(RuntimeException.class);
    }
}
