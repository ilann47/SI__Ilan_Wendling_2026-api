package com.kaneko.estacionamento.patio;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Só novas cobranças optam por esta regra; o histórico não é recalculado. */
public final class PatioCobrancaCentavos {
    private PatioCobrancaCentavos() {}
    public record Valor(
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal valorCalculado,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal valorDevido,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal diferencaArredondamento) {}

    static Valor calcular(BigDecimal base) {
        if(base==null || base.signum()<0) throw new ConflitoException("O valor calculado não pode ser negativo.");
        final BigDecimal calculado;
        try { calculado=base.setScale(6,RoundingMode.UNNECESSARY); }
        catch(ArithmeticException ex){throw new ConflitoException("O cálculo excede seis casas decimais.");}
        var devido=calculado.setScale(2,RoundingMode.HALF_UP).setScale(6);
        return new Valor(calculado,devido,devido.subtract(calculado));
    }
    static Valor paraCobrar(BigDecimal base) {
        var valor=calcular(base);
        if(base.signum()>0 && valor.valorDevido().signum()==0)
            throw new ConflitoException("A cobrança positiva precisa fechar em pelo menos um centavo.");
        return valor;
    }
}
