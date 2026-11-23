package com.kaneko.estacionamento.fiscal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaNotaEntradaResponse(int numeroParcela, int totalParcelas, BigDecimal valor,
        LocalDate dataVencimento, Long formaPagamentoId, String formaPagamentoNome) { }
