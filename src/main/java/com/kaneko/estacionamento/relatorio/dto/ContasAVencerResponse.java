package com.kaneko.estacionamento.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatorio de contas a vencer (4.28): titulos a pagar e a receber em aberto
 * (PENDENTE/PARCIAL) vencendo no periodo, com destaque dos vencidos e
 * totalizacao do saldo.
 */
public record ContasAVencerResponse(
        LocalDate inicio,
        LocalDate fim,
        List<Titulo> aPagar,
        List<Titulo> aReceber,
        BigDecimal totalAPagar,
        BigDecimal totalAReceber,
        BigDecimal vencidoAPagar,
        BigDecimal vencidoAReceber) {

    public record Titulo(
            String origem,
            Long id,
            String contraparte,
            LocalDate vencimento,
            BigDecimal saldo,
            boolean vencido,
            String situacao) {
    }
}
