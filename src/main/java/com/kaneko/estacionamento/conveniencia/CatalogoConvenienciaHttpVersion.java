package com.kaneko.estacionamento.conveniencia;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CatalogoConvenienciaHttpVersion {

    private static final Pattern ETAG_VERSAO = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");

    private CatalogoConvenienciaHttpVersion() {
    }

    static long extrair(String ifMatch) {
        Matcher matcher = ETAG_VERSAO.matcher(ifMatch);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "If-Match deve conter somente a versao esperada entre aspas.");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("A versao informada em If-Match e invalida.", ex);
        }
    }

    static String etag(Long version) {
        return "\"" + version + "\"";
    }
}
