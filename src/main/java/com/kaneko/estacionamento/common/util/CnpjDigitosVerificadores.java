package com.kaneko.estacionamento.common.util;

/** Dígitos verificadores para CNPJ canônico, numérico ou alfanumérico. */
public final class CnpjDigitosVerificadores {
    private static final int[] PESOS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CnpjDigitosVerificadores() {}

    public static boolean validos(String cnpj) {
        if (!cnpj.matches("[A-Z0-9]{12}[0-9]{2}")) return false;
        for (int tamanho = 12; tamanho <= 13; tamanho++) {
            int soma = 0;
            for (int i = 0; i < tamanho; i++) {
                // Em CNPJs alfanuméricos, cada caractere vale seu código ASCII menos 48.
                soma += (cnpj.charAt(i) - '0') * PESOS[13 - tamanho + i];
            }
            int resto = soma % 11;
            int digito = resto < 2 ? 0 : 11 - resto;
            if (digito != cnpj.charAt(tamanho) - '0') return false;
        }
        return true;
    }
}
