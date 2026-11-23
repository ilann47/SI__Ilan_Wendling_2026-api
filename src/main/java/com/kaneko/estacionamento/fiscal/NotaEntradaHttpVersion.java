package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class NotaEntradaHttpVersion {
    private NotaEntradaHttpVersion() { }
    public static long extrair(String valor) {
        if (valor == null) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                "Informe If-Match com a versão revisada da nota.");
        if (!valor.matches("\"[0-9]+\"")) throw new IllegalArgumentException("If-Match deve conter a versão entre aspas.");
        try { return Long.parseLong(valor.substring(1, valor.length() - 1)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Versão inválida."); }
    }
    public static void exigir(NotaEntrada nota, long versao) {
        if (nota.getVersion() == null || nota.getVersion() != versao)
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "A nota foi alterada. Recarregue e revise antes de confirmar.");
    }
    public static void pendente(NotaEntrada nota) {
        if (nota.getSituacao() != SituacaoNota.PENDENTE)
            throw new ConflitoException("Somente notas pendentes podem ser alteradas.");
    }
}
