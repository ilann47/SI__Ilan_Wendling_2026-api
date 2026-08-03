package com.kaneko.estacionamento.conveniencia.dto;

import com.kaneko.estacionamento.conveniencia.Categoria;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma Categoria.
 */
public record CategoriaResponse(
        Long id,
        String nome,
        String descricao,
        Boolean ativo,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Converte a entidade {@link Categoria} em sua representacao de saida. */
    public static CategoriaResponse from(Categoria categoria) {
        return new CategoriaResponse(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                categoria.getAtivo(),
                categoria.getVersion(),
                categoria.getCreatedAt(),
                categoria.getUpdatedAt());
    }
}
