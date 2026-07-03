package com.kaneko.estacionamento.pagamento.dto;

import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Representacao de saida de uma Condicao de Pagamento, com suas parcelas.
 */
public record CondicaoPagamentoResponse(
        Long id,
        String nome,
        Integer numeroParcelas,
        BigDecimal percentualJuros,
        BigDecimal percentualMulta,
        BigDecimal percentualDesconto,
        Boolean ativo,
        List<ParcelaResponse> parcelas,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Converte a entidade {@link CondicaoPagamento} (e suas parcelas) em sua representacao de saida. */
    public static CondicaoPagamentoResponse from(CondicaoPagamento condicao) {
        return new CondicaoPagamentoResponse(
                condicao.getId(),
                condicao.getNome(),
                condicao.getNumeroParcelas(),
                condicao.getPercentualJuros(),
                condicao.getPercentualMulta(),
                condicao.getPercentualDesconto(),
                condicao.getAtivo(),
                condicao.getParcelas().stream().map(ParcelaResponse::from).toList(),
                condicao.getCreatedAt(),
                condicao.getUpdatedAt());
    }
}
