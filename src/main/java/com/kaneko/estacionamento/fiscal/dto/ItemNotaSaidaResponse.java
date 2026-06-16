package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.fiscal.ItemNotaSaida;
import java.math.BigDecimal;

/**
 * Representacao de saida de um item da nota de saida.
 */
public record ItemNotaSaidaResponse(
        Long id,
        Long produtoId,
        String produtoNome,
        Integer sequencia,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorDesconto,
        BigDecimal percentualDesconto,
        BigDecimal valorTotal,
        BigDecimal rateioFrete,
        BigDecimal rateioSeguro,
        BigDecimal rateioOutras,
        BigDecimal custoPrecoFinal
) {

    public static ItemNotaSaidaResponse from(ItemNotaSaida item) {
        return new ItemNotaSaidaResponse(
                item.getId(),
                item.getProduto().getId(),
                item.getProduto().getNome(),
                item.getSequencia(),
                item.getQuantidade(),
                item.getValorUnitario(),
                item.getValorDesconto(),
                item.getPercentualDesconto(),
                item.getValorTotal(),
                item.getRateioFrete(),
                item.getRateioSeguro(),
                item.getRateioOutras(),
                item.getCustoPrecoFinal());
    }
}
