package com.kaneko.estacionamento.financeiro;

/**
 * Situacao de uma conta a pagar (ou despesa avulsa). Espelha o dominio dos
 * CHECKs {@code ck_conta_pagar_situacao} / {@code ck_contas_pagar_avulsa_situacao}.
 */
public enum SituacaoContaPagar {
    PENDENTE,
    PARCIAL,
    PAGA,
    CANCELADA
}
