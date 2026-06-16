package com.kaneko.estacionamento.rh.dto;

import com.kaneko.estacionamento.rh.Cargo;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Cargo.
 */
public record CargoResponse(
        Long id,
        String nome,
        String descricao,
        BigDecimal salarioBase,
        BigDecimal cargaHoraria,
        Boolean requerCnh,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static CargoResponse from(Cargo c) {
        return new CargoResponse(
                c.getId(),
                c.getNome(),
                c.getDescricao(),
                c.getSalarioBase(),
                c.getCargaHoraria(),
                c.getRequerCnh(),
                c.getAtivo(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
