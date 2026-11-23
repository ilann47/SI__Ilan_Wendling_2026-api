package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.CampoInvalidoException;
import com.kaneko.estacionamento.common.util.CnpjDigitosVerificadores;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Identificação fiscal da estadia, independente de alterações no cadastro de clientes. */
public record PatioTomador(
        @NotBlank @Size(max = 20) String documento,
        @NotBlank @Size(max = 160) String nome) {

    static PatioTomador normalizar(PatioTomador entrada) {
        if (entrada == null) return null;
        String informado = entrada.documento == null ? "" : entrada.documento.strip();
        String nome = entrada.nome == null ? "" : entrada.nome.strip();
        if (!informado.matches("[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}"
                + "|(?i:[a-z0-9]{12}[0-9]{2}|[a-z0-9]{2}\\.[a-z0-9]{3}\\.[a-z0-9]{3}/[a-z0-9]{4}-[0-9]{2})")) {
            throw documentoInvalido();
        }
        String doc = informado.replaceAll("[./-]", "").toUpperCase(Locale.ROOT);
        if (doc.chars().allMatch(c -> c == doc.charAt(0))
                || (doc.length() == 11 ? !cpfValido(doc) : !CnpjDigitosVerificadores.validos(doc))) {
            throw documentoInvalido();
        }
        if (nome.isBlank() || nome.length() > 160 || nome.codePoints().anyMatch(Character::isISOControl)) {
            throw new CampoInvalidoException("tomador.nome", "Informe o nome ou a razão social, com até 160 caracteres.");
        }
        return new PatioTomador(doc, nome);
    }

    static PatioTomador salvo(String documento, String nome) {
        return documento == null ? null : new PatioTomador(documento, nome);
    }

    private static CampoInvalidoException documentoInvalido() {
        return new CampoInvalidoException("tomador.documento", "Informe um CPF ou CNPJ válido.");
    }

    private static boolean cpfValido(String cpf) {
        for (int tamanho = 9; tamanho <= 10; tamanho++) {
            int soma = 0;
            for (int i = 0; i < tamanho; i++) soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
            if (digito(soma) != cpf.charAt(tamanho) - '0') return false;
        }
        return true;
    }

    private static int digito(int soma) {
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    @Override
    public String toString() {
        return "PatioTomador[identificação protegida]";
    }
}
