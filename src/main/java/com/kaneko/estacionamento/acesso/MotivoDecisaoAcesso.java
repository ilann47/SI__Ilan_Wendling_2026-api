package com.kaneko.estacionamento.acesso;

public enum MotivoDecisaoAcesso {
    AUTORIZADA,
    CREDENCIAL_INVALIDA,
    CREDENCIAL_INATIVA,
    FORA_DA_JANELA,
    EVENTO_FORA_DE_OPERACAO,
    PATIO_INCORRETO,
    ENTRADA_DUPLICADA,
    PATIO_LOTADO
}
