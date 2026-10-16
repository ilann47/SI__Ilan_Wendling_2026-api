package com.kaneko.estacionamento.estoque;

public class VersaoEstoqueDivergenteException extends RuntimeException {
    public VersaoEstoqueDivergenteException() {
        super("A versao esperada diverge do recurso persistido.");
    }
}
