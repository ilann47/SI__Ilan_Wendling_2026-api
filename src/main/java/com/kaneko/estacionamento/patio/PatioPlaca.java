package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.CampoInvalidoException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Identificador da portaria, com compatibilidade para as placas brasileiras já gravadas. */
final class PatioPlaca {
    static final int MAXIMO = 10;
    private static final String SUFIXO_BR = "[0-9][A-Z0-9][0-9]{2}";
    private static final Pattern COM_HIFEN = Pattern.compile("[A-Z]{3}-" + SUFIXO_BR);
    private static final Pattern SEM_HIFEN = Pattern.compile("[A-Z]{3}" + SUFIXO_BR);
    private static final Pattern MARGENS = Pattern.compile("(?U)^\\s+|\\s+$");

    // A coluna já contém a placa em maiúsculas; preserve hífens que fazem parte de um identificador livre.
    static final String SQL_BUSCA = "(CASE WHEN v.placa ~ '^" + COM_HIFEN.pattern()
            + "$' THEN replace(v.placa, '-', '') ELSE v.placa END)";

    private PatioPlaca() {}

    static String obrigatoria(String entrada) {
        var placa = normalizar(entrada);
        if (placa.isEmpty()) throw new CampoInvalidoException("placa", "Informe a placa ou o identificador do veículo.");
        return placa;
    }

    static String normalizar(String entrada) {
        if (entrada == null) return "";
        if (entrada.codePoints().anyMatch(c -> Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE)) {
            throw new CampoInvalidoException("placa", "Use apenas caracteres visíveis na placa.");
        }
        var placa = MARGENS.matcher(entrada).replaceAll("").toUpperCase(Locale.ROOT);
        if (placa.length() > MAXIMO) {
            throw new CampoInvalidoException("placa", "Informe até 10 caracteres na placa.");
        }
        return COM_HIFEN.matcher(placa).matches() ? placa.replace("-", "") : placa;
    }

    static Optional<String> grafiaLegada(String normalizada) {
        return SEM_HIFEN.matcher(normalizada).matches()
                ? Optional.of(normalizada.substring(0, 3) + "-" + normalizada.substring(3)) : Optional.empty();
    }
}
