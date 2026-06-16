package com.kaneko.estacionamento.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Relatorio de faturamento do dia/periodo (4.27). O {@code totalFaturado} soma
 * as origens por competencia (estacionamento + vendas + servicos); os
 * {@code recebimentos} sao o regime de caixa (titulos efetivamente recebidos no
 * periodo) e nao entram no total para evitar dupla contagem.
 */
public record FaturamentoDiaResponse(
        LocalDate inicio,
        LocalDate fim,
        BigDecimal estacionamento,
        BigDecimal vendas,
        BigDecimal servicos,
        BigDecimal totalFaturado,
        BigDecimal recebimentos) {
}
