package com.kaneko.estacionamento.credencial;

public class VersaoCredencialDivergenteException extends RuntimeException {
    public VersaoCredencialDivergenteException() {
        super("A credencial foi alterada por outra operacao.");
    }
}
