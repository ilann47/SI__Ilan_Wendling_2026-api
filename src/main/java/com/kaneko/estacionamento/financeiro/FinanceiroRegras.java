package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import java.math.BigDecimal;

final class FinanceiroRegras {
    private FinanceiroRegras() { }

    static BigDecimal total(BigDecimal original, BigDecimal desconto, BigDecimal juros,
            BigDecimal multa, BigDecimal informado) {
        for (BigDecimal valor : new BigDecimal[]{original, desconto, juros, multa}) {
            if (valor == null || valor.signum() < 0 || valor.stripTrailingZeros().scale() > 2) {
                throw new ConflitoException("Valores monetários devem ser não negativos e ter até duas casas decimais.");
            }
        }
        BigDecimal total = original.subtract(desconto).add(juros).add(multa);
        if (desconto.compareTo(original) > 0 || total.signum() < 0
                || (informado != null && informado.compareTo(total) != 0)) {
            throw new ConflitoException("O total deve corresponder ao valor original menos desconto, mais juros e multa.");
        }
        return total;
    }

    static void validarBaixa(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.stripTrailingZeros().scale() > 2) {
            throw new ConflitoException("A baixa deve ser positiva e ter até duas casas decimais.");
        }
    }
}
