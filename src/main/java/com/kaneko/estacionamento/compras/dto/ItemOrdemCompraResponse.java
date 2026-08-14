package com.kaneko.estacionamento.compras.dto;

import com.kaneko.estacionamento.compras.ItemOrdemCompra;
import java.math.BigDecimal;

public record ItemOrdemCompraResponse(
        Long id,
        Integer sequencia,
        Long produtoId,
        String produtoNome,
        BigDecimal quantidadePedida,
        BigDecimal quantidadeRecebida,
        BigDecimal quantidadePendente,
        BigDecimal valorUnitario,
        BigDecimal valorDesconto,
        BigDecimal valorTotal) {

    public static ItemOrdemCompraResponse from(ItemOrdemCompra item) {
        return new ItemOrdemCompraResponse(item.getId(), item.getSequencia(),
                item.getProduto().getId(), item.getProduto().getNome(),
                item.getQuantidadePedida(), item.getQuantidadeRecebida(),
                item.saldoReceber(), item.getValorUnitario(), item.getValorDesconto(),
                item.getValorTotal());
    }
}
