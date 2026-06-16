package com.kaneko.estacionamento.common.exception;

/**
 * Lancada quando as credenciais informadas no login sao invalidas. Mapeada para
 * HTTP 401 pelo {@code ApiExceptionHandler}.
 */
public class AutenticacaoException extends RuntimeException {

    public AutenticacaoException(String mensagem) {
        super(mensagem);
    }
}
