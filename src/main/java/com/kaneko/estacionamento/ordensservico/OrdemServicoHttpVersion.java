package com.kaneko.estacionamento.ordensservico;

final class OrdemServicoHttpVersion {
    private OrdemServicoHttpVersion() { }
    static String etag(Long version) { return "\"" + version + "\""; }
    static long extrair(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\"[0-9]+\"")) {
            throw new IllegalArgumentException("If-Match deve conter somente a versao esperada entre aspas.");
        }
        return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
    }
}
