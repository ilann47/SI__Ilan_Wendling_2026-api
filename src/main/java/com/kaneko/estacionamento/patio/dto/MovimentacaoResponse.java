package com.kaneko.estacionamento.patio.dto;

import com.kaneko.estacionamento.patio.Movimentacao;
import com.kaneko.estacionamento.patio.StatusMovimentacao;
import com.kaneko.estacionamento.patio.TipoMovimentacao;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma movimentacao de patio.
 */
public record MovimentacaoResponse(
        Long id,
        Long veiculoId,
        String veiculoPlaca,
        TipoMovimentacao tipo,
        Long mensalistaId,
        Long tarifaId,
        Long patioId,
        OffsetDateTime dataEntrada,
        OffsetDateTime dataSaida,
        BigDecimal valorCobrado,
        StatusMovimentacao status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static MovimentacaoResponse from(Movimentacao m) {
        return new MovimentacaoResponse(
                m.getId(),
                m.getVeiculo().getId(),
                m.getVeiculo().getPlaca(),
                m.getTipo(),
                m.getMensalista() != null ? m.getMensalista().getId() : null,
                m.getTarifa() != null ? m.getTarifa().getId() : null,
                m.getPatio() != null ? m.getPatio().getId() : null,
                m.getDataEntrada(),
                m.getDataSaida(),
                m.getValorCobrado(),
                m.getStatus(),
                m.getCreatedAt(),
                m.getUpdatedAt());
    }
}
