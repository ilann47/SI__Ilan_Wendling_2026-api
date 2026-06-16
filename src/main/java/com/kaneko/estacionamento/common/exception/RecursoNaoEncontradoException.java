package com.kaneko.estacionamento.common.exception;

/**
 * Lancada quando um recurso solicitado nao existe na base. Mapeada para
 * HTTP 404 pelo tratador global de erros.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String recurso, Object id) {
        super(recurso + " nao encontrado(a) para o identificador " + id + ".");
    }

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
