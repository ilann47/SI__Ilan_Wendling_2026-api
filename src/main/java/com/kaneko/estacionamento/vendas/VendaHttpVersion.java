package com.kaneko.estacionamento.vendas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VendaHttpVersion {
    private static final Pattern ETAG = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");
    private VendaHttpVersion() { }
    static long extrair(String value) {
        Matcher matcher = ETAG.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "If-Match deve conter somente a versao esperada entre aspas.");
        }
        return Long.parseLong(matcher.group(1));
    }
    static String etag(Long version) { return "\"" + version + "\""; }
}
