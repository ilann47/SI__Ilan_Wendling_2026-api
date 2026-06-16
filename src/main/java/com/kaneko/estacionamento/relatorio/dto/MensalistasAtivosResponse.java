package com.kaneko.estacionamento.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatorio de mensalistas ativos (4.30): contratos ATIVO com o valor mensal,
 * totalizando a receita recorrente.
 */
public record MensalistasAtivosResponse(
        int total,
        BigDecimal receitaMensalRecorrente,
        List<ItemMensalista> itens) {

    public record ItemMensalista(
            Long mensalistaId,
            String cliente,
            String placa,
            String modelo,
            BigDecimal valorMensal,
            LocalDate dataInicio) {
    }
}
