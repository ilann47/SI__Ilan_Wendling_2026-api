package com.kaneko.estacionamento.evento;

public class VersaoEventoDivergenteException extends RuntimeException {
    public VersaoEventoDivergenteException() {
        super("A versao esperada do evento diverge da versao atual.");
    }
}
