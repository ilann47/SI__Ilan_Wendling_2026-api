package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class PatioDinheiro {
    record Valores(BigDecimal recebido, BigDecimal troco) {}
    private PatioDinheiro() {}

    static Valores conferir(PatioCaixaService.Meio meio, BigDecimal devido, BigDecimal recebido) {
        if (meio != PatioCaixaService.Meio.DINHEIRO) {
            if (recebido != null) throw new ConflitoException("Informe o valor recebido somente para dinheiro.");
            return new Valores(null, null);
        }
        BigDecimal valor;
        try {
            valor = (recebido == null ? devido : recebido).setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new ConflitoException("Informe no máximo seis casas decimais.");
        }
        if (valor.compareTo(devido) < 0) throw new ConflitoException("O valor recebido é inferior ao valor devido.");
        return new Valores(valor, valor.subtract(devido));
    }
}
