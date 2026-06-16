package com.kaneko.estacionamento.fiscal;

/**
 * Modalidade de frete de uma nota fiscal. Espelha os CHECKs
 * {@code ck_nota_entrada_frete} / {@code ck_nota_saida_frete}.
 */
public enum TipoFrete {
    /** Por conta do remetente (incluso). */
    CIF,
    /** Por conta do destinatario. */
    FOB,
    /** Sem frete. */
    SEM
}
