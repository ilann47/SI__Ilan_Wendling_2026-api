package com.kaneko.estacionamento.inventario.alocacao;

public class VersaoAlocacaoDivergenteException extends RuntimeException {
    public VersaoAlocacaoDivergenteException() {
        super("A alocacao foi alterada por outra operacao.");
    }
}
