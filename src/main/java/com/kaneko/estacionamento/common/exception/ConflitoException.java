package com.kaneko.estacionamento.common.exception;

/**
 * Lancada quando uma operacao viola uma regra de unicidade ou de negocio
 * (por exemplo, nome ja cadastrado). Mapeada para HTTP 409.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
