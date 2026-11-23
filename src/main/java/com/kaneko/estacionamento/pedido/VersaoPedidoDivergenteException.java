package com.kaneko.estacionamento.pedido;

public class VersaoPedidoDivergenteException extends RuntimeException {
    public VersaoPedidoDivergenteException() {
        super("A versao do pedido divergiu.");
    }
}
