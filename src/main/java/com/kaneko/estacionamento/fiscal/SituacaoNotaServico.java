package com.kaneko.estacionamento.fiscal;

/**
 * Situacao de uma nota de servico (NFS-e). Espelha {@code ck_nota_servico_situacao}.
 */
public enum SituacaoNotaServico {
    PENDENTE,
    EMITIDA,
    CANCELADA
}
