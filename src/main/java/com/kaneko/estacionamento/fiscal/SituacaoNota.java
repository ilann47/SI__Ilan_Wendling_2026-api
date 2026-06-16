package com.kaneko.estacionamento.fiscal;

/**
 * Situacao de uma nota de entrada ou de saida. Uma nota nasce {@code PENDENTE},
 * pode ser {@code CONFIRMADA} (efetiva estoque + gera titulos) ou
 * {@code CANCELADA}. Espelha {@code ck_nota_entrada_situacao} /
 * {@code ck_nota_saida_situacao}.
 */
public enum SituacaoNota {
    PENDENTE,
    CONFIRMADA,
    CANCELADA
}
