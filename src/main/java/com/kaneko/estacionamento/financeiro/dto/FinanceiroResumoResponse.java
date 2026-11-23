package com.kaneko.estacionamento.financeiro.dto;

import java.math.BigDecimal;

/** Agregação SQL do conjunto filtrado completo. Saldo desconsidera títulos cancelados. */
public record FinanceiroResumoResponse(long quantidade, BigDecimal valorOriginal,
        BigDecimal valorTotal, BigDecimal valorBaixado, BigDecimal saldo) { }
