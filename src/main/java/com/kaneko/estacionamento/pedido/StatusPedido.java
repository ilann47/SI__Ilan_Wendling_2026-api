package com.kaneko.estacionamento.pedido;

public enum StatusPedido {
    CRIADO,
    AGUARDANDO_PAGAMENTO,
    CONFIRMADO,
    ATENDIDO,
    CONCLUIDO,
    EXPIRADO,
    CANCELADO,
    EM_REEMBOLSO,
    REEMBOLSADO,
    CANCELADO_COM_PENDENCIA
}
