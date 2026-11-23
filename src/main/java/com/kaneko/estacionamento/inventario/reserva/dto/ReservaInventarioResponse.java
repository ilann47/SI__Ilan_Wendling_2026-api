package com.kaneko.estacionamento.inventario.reserva.dto;

import com.kaneko.estacionamento.inventario.reserva.ReservaInventario;
import com.kaneko.estacionamento.inventario.reserva.StatusReservaInventario;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReservaInventarioResponse(
        Long id,
        Long organizationId,
        Long eventId,
        Long parkingProductId,
        Integer quantity,
        String channel,
        String buyerReference,
        Long priceTierId,
        BigDecimal unitPrice,
        String currency,
        StatusReservaInventario status,
        OffsetDateTime expiresAt,
        OffsetDateTime finalizedAt,
        String finalizationReason,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ReservaInventarioResponse from(ReservaInventario reserva) {
        return new ReservaInventarioResponse(
                reserva.getId(), reserva.getOrganizacaoId(), reserva.getEventoId(),
                reserva.getProdutoId(), reserva.getQuantidade(), reserva.getCanal(),
                reserva.getReferenciaComprador(), reserva.getLotePrecoId(),
                reserva.getPrecoUnitario(), reserva.getMoeda(), reserva.getStatus(),
                reserva.getExpiraEm(), reserva.getFinalizadaEm(),
                reserva.getMotivoFinalizacao(), reserva.getVersion(),
                reserva.getCreatedAt(), reserva.getUpdatedAt());
    }
}
