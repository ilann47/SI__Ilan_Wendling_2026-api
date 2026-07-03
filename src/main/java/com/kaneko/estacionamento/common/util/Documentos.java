package com.kaneko.estacionamento.common.util;

/**
 * Utilitario para normalizacao de documentos (CPF/CNPJ). Armazena-se apenas os
 * digitos, tornando a unicidade robusta a mascaras diferentes (por exemplo
 * {@code 123.456.789-09} e {@code 12345678909} passam a colidir corretamente).
 */
public final class Documentos {

    private Documentos() {
    }

    /**
     * Retorna somente os digitos do valor. Quando o valor nao contem nenhum
     * digito, devolve o texto original apenas aparado, para nao mascarar um
     * erro de preenchimento.
     */
    public static String soDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        return digitos.isEmpty() ? valor.trim() : digitos;
    }
}
