package com.kaneko.estacionamento.pedido.dto;

import com.kaneko.estacionamento.pedido.Pedido;
import com.kaneko.estacionamento.pedido.StatusPedido;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        Long organizationId,
        Long eventId,
        Long inventoryHoldId,
        String number,
        String channel,
        String vehiclePlate,
        StatusPedido status,
        CompradorPedidoResponse buyer,
        List<ItemPedidoResponse> items,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal fees,
        BigDecimal total,
        String currency,
        OffsetDateTime expiresAt,
        OffsetDateTime finalizedAt,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static PedidoResponse from(Pedido pedido) {
        return new PedidoResponse(
                pedido.getId(), pedido.getOrganizacaoId(), pedido.getEventoId(),
                pedido.getReservaInventarioId(), pedido.getNumero(), pedido.getCanal(),
                pedido.getPlacaVeiculo(), pedido.getStatus(),
                CompradorPedidoResponse.from(pedido.getComprador()),
                pedido.getItens().stream().map(ItemPedidoResponse::from).toList(),
                pedido.getSubtotal(), pedido.getDesconto(), pedido.getTaxa(),
                pedido.getTotal(), pedido.getMoeda(), pedido.getExpiraEm(),
                pedido.getFinalizadoEm(), pedido.getVersion(), pedido.getCreatedAt(),
                pedido.getUpdatedAt());
    }
}
