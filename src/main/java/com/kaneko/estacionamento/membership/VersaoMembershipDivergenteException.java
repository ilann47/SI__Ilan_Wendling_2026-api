package com.kaneko.estacionamento.membership;

public class VersaoMembershipDivergenteException extends RuntimeException {

    public VersaoMembershipDivergenteException(long esperada, long atual) {
        super("A versao esperada " + esperada + " difere da versao atual " + atual + ".");
    }
}
