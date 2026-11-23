package com.kaneko.estacionamento.common.security;

public class TokenContextoInvalidoException extends RuntimeException {

    public TokenContextoInvalidoException() {
        super("O contexto organizacional do token nao e mais valido.");
    }
}
