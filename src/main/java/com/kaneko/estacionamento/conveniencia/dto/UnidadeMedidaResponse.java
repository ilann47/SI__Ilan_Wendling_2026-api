package com.kaneko.estacionamento.conveniencia.dto;

import com.kaneko.estacionamento.conveniencia.UnidadeMedida;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma UnidadeMedida.
 */
public record UnidadeMedidaResponse(
        Long id,
        String nome,
        String sigla,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Converte a entidade {@link UnidadeMedida} em sua representacao de saida. */
    public static UnidadeMedidaResponse from(UnidadeMedida unidade) {
        return new UnidadeMedidaResponse(
                unidade.getId(),
                unidade.getNome(),
                unidade.getSigla(),
                unidade.getAtivo(),
                unidade.getCreatedAt(),
                unidade.getUpdatedAt());
    }
}
