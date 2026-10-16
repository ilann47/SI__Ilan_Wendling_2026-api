package com.kaneko.estacionamento.conveniencia.dto;

import com.kaneko.estacionamento.conveniencia.Servico;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Servico.
 */
public record ServicoResponse(
        Long id,
        String nome,
        String descricao,
        BigDecimal valorPadrao,
        Boolean ativo,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Converte a entidade {@link Servico} em sua representacao de saida. */
    public static ServicoResponse from(Servico servico) {
        return new ServicoResponse(
                servico.getId(),
                servico.getNome(),
                servico.getDescricao(),
                servico.getValorPadrao(),
                servico.getAtivo(),
                servico.getVersion(),
                servico.getCreatedAt(),
                servico.getUpdatedAt());
    }
}
