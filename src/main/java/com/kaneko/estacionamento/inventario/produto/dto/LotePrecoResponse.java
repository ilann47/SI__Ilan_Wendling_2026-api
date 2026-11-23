package com.kaneko.estacionamento.inventario.produto.dto;

import com.kaneko.estacionamento.inventario.produto.LotePreco;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LotePrecoResponse(
        Long id,
        Long organizationId,
        Long parkingProductId,
        String name,
        BigDecimal price,
        String currency,
        OffsetDateTime salesStartsAt,
        OffsetDateTime salesEndsAt,
        Integer quantity,
        Integer priority,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static LotePrecoResponse from(LotePreco lote) {
        return new LotePrecoResponse(
                lote.getId(), lote.getOrganizacaoId(), lote.getProdutoId(), lote.getNome(),
                lote.getPreco(), lote.getMoeda(), lote.getInicioVenda(), lote.getFimVenda(),
                lote.getQuantidade(), lote.getPrioridade(), lote.getVersion(),
                lote.getCreatedAt(), lote.getUpdatedAt());
    }
}
