package com.kaneko.estacionamento.financeiro;

/**
 * Situacao de uma conta a receber. Espelha {@code ck_conta_receber_situacao}.
 */
public enum SituacaoContaReceber {
    PENDENTE,
    PARCIAL,
    RECEBIDA,
    CANCELADA
}
