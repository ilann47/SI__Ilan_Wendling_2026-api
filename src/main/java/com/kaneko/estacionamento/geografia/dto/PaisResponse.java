package com.kaneko.estacionamento.geografia.dto;

import com.kaneko.estacionamento.geografia.Pais;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Pais.
 */
public record PaisResponse(
        Long id,
        String nome,
        String sigla,
        String codigo,
        String nacionalidade,
        String moeda,
        String ddi,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Converte a entidade {@link Pais} em sua representacao de saida. */
    public static PaisResponse from(Pais pais) {
        return new PaisResponse(
                pais.getId(),
                pais.getNome(),
                pais.getSigla(),
                pais.getCodigo(),
                pais.getNacionalidade(),
                pais.getMoeda(),
                pais.getDdi(),
                pais.getAtivo(),
                pais.getCreatedAt(),
                pais.getUpdatedAt());
    }
}
