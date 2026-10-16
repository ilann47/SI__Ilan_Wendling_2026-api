package com.kaneko.estacionamento.compras;

public class VersaoOrdemCompraDivergenteException extends RuntimeException {
    public VersaoOrdemCompraDivergenteException() {
        super("A ordem de compra foi alterada por outra operacao.");
    }
}
