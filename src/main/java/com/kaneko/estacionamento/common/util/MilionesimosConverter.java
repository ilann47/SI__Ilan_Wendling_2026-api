package com.kaneko.estacionamento.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Converte apenas na fronteira JDBC. Valores de domínio ficam nas unidades originais. */
@Converter(autoApply = false)
public class MilionesimosConverter implements AttributeConverter<BigDecimal, BigDecimal> {
    public static final int ESCALA = 6;

    @Override
    public BigDecimal convertToDatabaseColumn(BigDecimal valor) {
        return valor == null ? null : valor.movePointRight(ESCALA).setScale(0, RoundingMode.UNNECESSARY);
    }

    @Override
    public BigDecimal convertToEntityAttribute(BigDecimal inteiro) {
        return inteiro == null ? null : new BigDecimal(inteiro.toBigIntegerExact(), ESCALA);
    }

    public static BigDecimal ler(BigDecimal atual, BigDecimal legado) {
        return atual != null || legado == null ? atual : legado.setScale(ESCALA, RoundingMode.UNNECESSARY);
    }
}
