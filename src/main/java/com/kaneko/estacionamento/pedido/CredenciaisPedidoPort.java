package com.kaneko.estacionamento.pedido;

import java.time.OffsetDateTime;

public interface CredenciaisPedidoPort {
    int bloquearDoPedido(
            Long organizacaoId, Long pedidoId, String motivo,
            OffsetDateTime instante);
}
