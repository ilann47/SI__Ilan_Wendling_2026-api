package com.kaneko.estacionamento.conveniencia;

public class VersaoCatalogoConvenienciaDivergenteException extends RuntimeException {

    public VersaoCatalogoConvenienciaDivergenteException(long esperada, long atual) {
        super("A versao esperada " + esperada + " difere da versao atual " + atual + ".");
    }
}
