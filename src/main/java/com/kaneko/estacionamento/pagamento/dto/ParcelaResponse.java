package com.kaneko.estacionamento.pagamento.dto;

import com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento;
import java.math.BigDecimal;

/**
 * Representacao de saida de uma parcela da condicao de pagamento, com a forma de
 * pagamento referenciada de forma resumida (id e nome), quando houver.
 */
public record ParcelaResponse(
        Long id,
        Integer numero,
        Integer dias,
        BigDecimal percentual,
        Long formaPagamentoId,
        String formaPagamentoNome
) {

    public static ParcelaResponse from(ParcelaCondicaoPagamento parcela) {
        return new ParcelaResponse(
                parcela.getId(),
                parcela.getNumero(),
                parcela.getDias(),
                parcela.getPercentual(),
                parcela.getFormaPagamento() != null ? parcela.getFormaPagamento().getId() : null,
                parcela.getFormaPagamento() != null ? parcela.getFormaPagamento().getNome() : null);
    }
}
