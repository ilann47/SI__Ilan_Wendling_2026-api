package com.kaneko.estacionamento.logistica.dto;

import com.kaneko.estacionamento.logistica.VeiculoFrota;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Veiculo de Frota.
 */
public record VeiculoFrotaResponse(
        Long id,
        String placa,
        String modelo,
        String marca,
        Integer ano,
        BigDecimal capacidade,
        String observacao,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static VeiculoFrotaResponse from(VeiculoFrota v) {
        return new VeiculoFrotaResponse(
                v.getId(),
                v.getPlaca(),
                v.getModelo(),
                v.getMarca(),
                v.getAno(),
                v.getCapacidade(),
                v.getObservacao(),
                v.getAtivo(),
                v.getCreatedAt(),
                v.getUpdatedAt());
    }
}
