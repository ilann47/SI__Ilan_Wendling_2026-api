package com.kaneko.estacionamento.inventario.disponibilidade.dto;

import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ItemDisponibilidadeResponse(
        Long productId,
        Long parkingAllocationId,
        String name,
        CategoriaProduto category,
        DireitoProduto right,
        OffsetDateTime accessStartsAt,
        OffsetDateTime accessEndsAt,
        int availableQuantity,
        Long priceTierId,
        String priceTierName,
        BigDecimal price,
        String currency,
        OffsetDateTime salesEndsAt) {}
