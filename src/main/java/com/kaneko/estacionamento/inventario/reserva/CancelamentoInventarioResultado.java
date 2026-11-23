package com.kaneko.estacionamento.inventario.reserva;

public record CancelamentoInventarioResultado(
        int quantidadeLiberada,
        StatusReservaInventario statusReserva
) {
}
