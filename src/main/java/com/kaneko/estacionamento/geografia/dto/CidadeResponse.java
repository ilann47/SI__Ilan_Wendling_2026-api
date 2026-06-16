package com.kaneko.estacionamento.geografia.dto;

import com.kaneko.estacionamento.geografia.Cidade;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma Cidade, com o estado referenciado de forma
 * resumida (id e nome).
 */
public record CidadeResponse(
        Long id,
        String nome,
        String ddd,
        String codigoIbge,
        Long estadoId,
        String estadoNome,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static CidadeResponse from(Cidade cidade) {
        return new CidadeResponse(
                cidade.getId(),
                cidade.getNome(),
                cidade.getDdd(),
                cidade.getCodigoIbge(),
                cidade.getEstado().getId(),
                cidade.getEstado().getNome(),
                cidade.getAtivo(),
                cidade.getCreatedAt(),
                cidade.getUpdatedAt());
    }
}
