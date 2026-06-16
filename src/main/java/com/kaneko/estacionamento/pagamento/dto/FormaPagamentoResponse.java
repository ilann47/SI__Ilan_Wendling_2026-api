package com.kaneko.estacionamento.pagamento.dto;

import com.kaneko.estacionamento.pagamento.FormaPagamento;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma Forma de Pagamento.
 */
public record FormaPagamentoResponse(
        Long id,
        String nome,
        String descricao,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Converte a entidade {@link FormaPagamento} em sua representacao de saida. */
    public static FormaPagamentoResponse from(FormaPagamento forma) {
        return new FormaPagamentoResponse(
                forma.getId(),
                forma.getNome(),
                forma.getDescricao(),
                forma.getAtivo(),
                forma.getCreatedAt(),
                forma.getUpdatedAt());
    }
}
