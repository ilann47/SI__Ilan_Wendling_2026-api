package com.kaneko.estacionamento.common.exception;

import java.util.Map;

/**
 * Lancada quando uma operacao viola uma regra de unicidade ou de negocio
 * (por exemplo, nome ja cadastrado). Mapeada para HTTP 409.
 */
public class ConflitoException extends RuntimeException {

    private final Map<String, String> erros;

    public ConflitoException(String mensagem) {
        super(mensagem);
        this.erros = Map.of();
    }

    public ConflitoException(String mensagem, String campo) {
        super(mensagem);
        this.erros = Map.of(campo, mensagem);
    }

    public Map<String, String> erros() {
        return erros;
    }
}
