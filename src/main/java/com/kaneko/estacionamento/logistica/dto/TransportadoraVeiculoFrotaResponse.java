package com.kaneko.estacionamento.logistica.dto;

import com.kaneko.estacionamento.logistica.TransportadoraVeiculoFrota;
import java.time.OffsetDateTime;

/**
 * Representacao de saida do vinculo transportadora-veiculo (frota).
 */
public record TransportadoraVeiculoFrotaResponse(
        Long id,
        Long transportadoraId,
        String transportadoraNome,
        Long veiculoFrotaId,
        String veiculoFrotaPlaca,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static TransportadoraVeiculoFrotaResponse from(TransportadoraVeiculoFrota tvf) {
        return new TransportadoraVeiculoFrotaResponse(
                tvf.getId(),
                tvf.getTransportadora().getId(),
                tvf.getTransportadora().getNome(),
                tvf.getVeiculoFrota().getId(),
                tvf.getVeiculoFrota().getPlaca(),
                tvf.getAtivo(),
                tvf.getCreatedAt(),
                tvf.getUpdatedAt());
    }
}
