package com.kaneko.estacionamento.geografia.dto;

import com.kaneko.estacionamento.geografia.Estado;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Estado, com o pais referenciado de forma
 * resumida (id e nome).
 */
public record EstadoResponse(
        Long id,
        String nome,
        String uf,
        Long paisId,
        String paisNome,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static EstadoResponse from(Estado estado) {
        return new EstadoResponse(
                estado.getId(),
                estado.getNome(),
                estado.getUf(),
                estado.getPais().getId(),
                estado.getPais().getNome(),
                estado.getAtivo(),
                estado.getCreatedAt(),
                estado.getUpdatedAt());
    }
}
