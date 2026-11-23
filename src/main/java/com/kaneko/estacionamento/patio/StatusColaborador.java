package com.kaneko.estacionamento.patio;

/**
 * Situacao de um colaborador dentro de um patio. Espelha {@code ck_colaborador_status}.
 */
public enum StatusColaborador {
    /** Convite enviado, ainda nao aceito. */
    CONVIDADO,
    /** Colaborador ativo, operando o patio. */
    ATIVO,
    /** Colaborador desativado (sem acesso). */
    INATIVO
}
