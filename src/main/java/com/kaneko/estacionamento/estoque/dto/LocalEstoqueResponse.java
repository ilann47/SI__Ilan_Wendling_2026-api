package com.kaneko.estacionamento.estoque.dto;

import com.kaneko.estacionamento.estoque.LocalEstoque;
import java.time.OffsetDateTime;

public record LocalEstoqueResponse(
        Long id,
        String nome,
        Boolean ativo,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static LocalEstoqueResponse from(LocalEstoque local) {
        return new LocalEstoqueResponse(local.getId(), local.getNome(), local.getAtivo(),
                local.getVersion(), local.getCreatedAt(), local.getUpdatedAt());
    }
}
