package com.kaneko.estacionamento.pedido.dto;

import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import com.kaneko.estacionamento.pedido.ItemPedido;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ItemPedidoResponse(
        Long id,
        Long parkingProductId,
        Long parkingAllocationId,
        Long parkingFacilityId,
        Long priceTierId,
        String productName,
        CategoriaProduto category,
        DireitoProduto entitlement,
        OffsetDateTime accessStartsAt,
        OffsetDateTime accessEndsAt,
        Integer quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal total) {

    public static ItemPedidoResponse from(ItemPedido item) {
        return new ItemPedidoResponse(
                item.getId(), item.getProdutoId(), item.getAlocacaoId(),
                item.getPatioId(), item.getLotePrecoId(), item.getNomeProduto(),
                item.getCategoria(),
                item.getDireito(), item.getInicioAcesso(), item.getFimAcesso(),
                item.getQuantidade(), item.getPrecoUnitario(), item.getMoeda(),
                item.getTotal());
    }
}
