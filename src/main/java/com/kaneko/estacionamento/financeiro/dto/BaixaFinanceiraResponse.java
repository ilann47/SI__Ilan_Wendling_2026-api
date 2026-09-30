package com.kaneko.estacionamento.financeiro.dto;

import com.kaneko.estacionamento.financeiro.BaixaFinanceira;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BaixaFinanceiraResponse(Long id, BigDecimal valor, LocalDate data, Long atorId,
        String atorNome, boolean saldoAnterior, OffsetDateTime registradoEm) {
    public static BaixaFinanceiraResponse from(BaixaFinanceira b) {
        return new BaixaFinanceiraResponse(b.getId(), b.getValor(), b.getData(), b.getAtorId(),
                b.getAtorNome(), b.isSaldoAnterior(), b.getCreatedAt());
    }
}
