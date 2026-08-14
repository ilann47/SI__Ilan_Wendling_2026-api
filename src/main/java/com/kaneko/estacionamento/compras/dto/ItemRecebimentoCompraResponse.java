package com.kaneko.estacionamento.compras.dto;

import com.kaneko.estacionamento.compras.ItemRecebimentoCompra;
import java.math.BigDecimal;

public record ItemRecebimentoCompraResponse(
        Long id,
        Long itemOrdemCompraId,
        Long produtoId,
        String produtoNome,
        BigDecimal quantidade,
        BigDecimal custoUnitario,
        Long movimentoEstoqueId) {

    public static ItemRecebimentoCompraResponse from(ItemRecebimentoCompra item) {
        return new ItemRecebimentoCompraResponse(item.getId(),
                item.getItemOrdemCompra().getId(), item.getProduto().getId(),
                item.getProduto().getNome(), item.getQuantidade(), item.getCustoUnitario(),
                item.getMovimentoEstoqueId());
    }
}
