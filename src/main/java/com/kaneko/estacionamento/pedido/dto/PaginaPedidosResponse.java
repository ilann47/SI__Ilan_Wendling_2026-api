package com.kaneko.estacionamento.pedido.dto;

import java.util.List;

public record PaginaPedidosResponse(
        List<PedidoResponse> items,
        Long nextCursor,
        boolean hasMore) {
}
