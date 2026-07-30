package com.kaneko.estacionamento.pedido.dto;

import com.kaneko.estacionamento.pedido.CancelamentoPedido;
import com.kaneko.estacionamento.pedido.StatusPedido;
import java.time.OffsetDateTime;

public record CancelamentoPedidoResponse(
        Long id,
        Long orderId,
        StatusPedido orderStatus,
        Boolean refundRequired,
        Integer releasedQuantity,
        Integer blockedCredentials,
        String reason,
        Long orderVersion,
        Long version,
        OffsetDateTime requestedAt
) {
    public static CancelamentoPedidoResponse from(CancelamentoPedido c) {
        return new CancelamentoPedidoResponse(
                c.getId(), c.getPedido().getId(), c.getStatusPedido(),
                c.getReembolsoNecessario(), c.getQuantidadeLiberada(),
                c.getCredenciaisBloqueadas(), c.getMotivo(),
                c.getPedido().getVersion(), c.getVersion(), c.getCreatedAt());
    }
}
