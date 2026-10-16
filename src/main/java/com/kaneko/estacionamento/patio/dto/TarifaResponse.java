package com.kaneko.estacionamento.patio.dto;

import com.kaneko.estacionamento.patio.Tarifa;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma Tarifa.
 */
public record TarifaResponse(
        Long id,
        Long organizacaoId,
        Long version,
        String descricao,
        BigDecimal valorHora,
        BigDecimal valorDiaria,
        Integer toleranciaMin,
        BigDecimal valorMensalidade,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static TarifaResponse from(Tarifa tarifa) {
        return new TarifaResponse(
                tarifa.getId(),
                tarifa.getOrganizacaoId(),
                tarifa.getVersion(),
                tarifa.getDescricao(),
                tarifa.getValorHora(),
                tarifa.getValorDiaria(),
                tarifa.getToleranciaMin(),
                tarifa.getValorMensalidade(),
                tarifa.getAtivo(),
                tarifa.getCreatedAt(),
                tarifa.getUpdatedAt());
    }
}
