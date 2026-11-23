package com.kaneko.estacionamento.organizacao;

public class VersaoOrganizacaoDivergenteException extends RuntimeException {

    public VersaoOrganizacaoDivergenteException(long esperada, long atual) {
        super("A versao esperada " + esperada + " difere da versao atual " + atual + ".");
    }
}
