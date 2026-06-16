package com.kaneko.estacionamento.conveniencia.dto;

import com.kaneko.estacionamento.conveniencia.Marca;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma Marca.
 */
public record MarcaResponse(
        Long id,
        String nome,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Converte a entidade {@link Marca} em sua representacao de saida. */
    public static MarcaResponse from(Marca marca) {
        return new MarcaResponse(
                marca.getId(),
                marca.getNome(),
                marca.getAtivo(),
                marca.getCreatedAt(),
                marca.getUpdatedAt());
    }
}
