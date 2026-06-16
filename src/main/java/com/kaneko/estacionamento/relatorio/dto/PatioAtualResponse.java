package com.kaneko.estacionamento.relatorio.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Relatorio de patio atual (4.26): veiculos com movimentacao em aberto, com o
 * tempo de permanencia decorrido e o totalizador por tipo.
 */
public record PatioAtualResponse(List<ItemPatio> itens, ResumoPatio resumo) {

    public record ItemPatio(
            Long movimentacaoId,
            String placa,
            String modelo,
            String tipo,
            OffsetDateTime dataEntrada,
            long minutosPermanencia) {
    }

    public record ResumoPatio(long totalVeiculos, long avulsos, long mensalistas) {
    }
}
