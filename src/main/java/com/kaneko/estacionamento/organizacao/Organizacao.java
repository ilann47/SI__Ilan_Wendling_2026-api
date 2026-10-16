package com.kaneko.estacionamento.organizacao;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.common.util.Documentos;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tenant empresarial e raiz do contexto de Organizacoes.
 *
 * <p>O documento identifica imutavelmente a organizacao. O provisionamento e
 * concluido de forma sincrona nesta primeira fase: a factory passa pelo estado
 * {@link StatusOrganizacao#PROVISIONANDO} e somente devolve a instancia apos a
 * transicao para {@link StatusOrganizacao#ATIVA}.
 */
@Entity
@Table(name = "organizacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organizacao extends AuditableEntity {

    private static final Pattern REGIAO_ISO_3166_2 =
            Pattern.compile("^[A-Z]{2}-[A-Z0-9]{1,3}$");
    private static final Pattern CODIGO_PLANO =
            Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");

    @Column(name = "documento", nullable = false, length = 14, updatable = false)
    private String documento;

    @Column(name = "razao_social", nullable = false, length = 160)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 160)
    private String nomeFantasia;

    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda;

    @Column(name = "fuso_horario", nullable = false, length = 60)
    private String fusoHorario;

    /** Codigo ISO 3166-2 da subdivisao operacional/regulatoria, como BR-PR. */
    @Column(name = "regiao", nullable = false, length = 8)
    private String regiao;

    @Column(name = "plano", nullable = false, length = 30)
    private String plano;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusOrganizacao status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Organizacao provisionar(
            String documento,
            String razaoSocial,
            String nomeFantasia,
            String moeda,
            String fusoHorario,
            String regiao,
            String plano) {
        Organizacao organizacao = new Organizacao();
        organizacao.status = StatusOrganizacao.PROVISIONANDO;
        organizacao.documento = normalizarEValidarCnpj(documento);
        organizacao.aplicarCadastro(razaoSocial, nomeFantasia, moeda, fusoHorario, regiao, plano);
        organizacao.ativarProvisionamento();
        return organizacao;
    }

    public void atualizarCadastro(
            String razaoSocial,
            String nomeFantasia,
            String moeda,
            String fusoHorario,
            String regiao,
            String plano) {
        if (status == StatusOrganizacao.ENCERRADA) {
            throw new IllegalStateException("A organizacao encerrada nao pode ser atualizada.");
        }
        aplicarCadastro(razaoSocial, nomeFantasia, moeda, fusoHorario, regiao, plano);
    }

    public void suspender() {
        if (status == StatusOrganizacao.ENCERRADA) {
            throw new IllegalStateException("ENCERRADA e um estado terminal.");
        }
        if (status != StatusOrganizacao.ATIVA) {
            throw new IllegalStateException("Somente uma organizacao ativa pode ser suspensa.");
        }
        status = StatusOrganizacao.SUSPENSA;
    }

    public void reativar() {
        if (status == StatusOrganizacao.ENCERRADA) {
            throw new IllegalStateException("ENCERRADA e um estado terminal.");
        }
        if (status != StatusOrganizacao.SUSPENSA) {
            throw new IllegalStateException("Somente uma organizacao suspensa pode ser reativada.");
        }
        status = StatusOrganizacao.ATIVA;
    }

    public void encerrar() {
        if (status == StatusOrganizacao.ENCERRADA) {
            return;
        }
        status = StatusOrganizacao.ENCERRADA;
    }

    private void ativarProvisionamento() {
        if (status != StatusOrganizacao.PROVISIONANDO) {
            throw new IllegalStateException("A organizacao nao esta em provisionamento.");
        }
        status = StatusOrganizacao.ATIVA;
    }

    private void aplicarCadastro(
            String razaoSocial,
            String nomeFantasia,
            String moeda,
            String fusoHorario,
            String regiao,
            String plano) {
        this.razaoSocial = textoObrigatorio(razaoSocial, "razao social", 160);
        this.nomeFantasia = textoOpcional(nomeFantasia, "nome fantasia", 160);
        this.moeda = normalizarMoeda(moeda);
        this.fusoHorario = validarFuso(fusoHorario);
        this.regiao = normalizarRegiao(regiao);
        this.plano = normalizarPlano(plano);
    }

    private static String normalizarEValidarCnpj(String valor) {
        String documento = Documentos.soDigitos(valor);
        if (!cnpjValido(documento)) {
            throw new IllegalArgumentException("O documento empresarial deve ser um CNPJ valido.");
        }
        return documento;
    }

    private static String normalizarMoeda(String valor) {
        String codigo = textoObrigatorio(valor, "moeda", 3).toUpperCase(Locale.ROOT);
        if (codigo.length() != 3) {
            throw new IllegalArgumentException("A moeda deve ser um codigo ISO 4217 de tres letras.");
        }
        try {
            Currency.getInstance(codigo);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A moeda deve ser um codigo ISO 4217 valido.", ex);
        }
        return codigo;
    }

    private static String validarFuso(String valor) {
        String fuso = textoObrigatorio(valor, "fuso horario", 60);
        try {
            return ZoneId.of(fuso).getId();
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("O fuso horario deve ser um identificador IANA valido.", ex);
        }
    }

    private static String normalizarRegiao(String valor) {
        String codigo = textoObrigatorio(valor, "regiao", 8).toUpperCase(Locale.ROOT);
        if (!REGIAO_ISO_3166_2.matcher(codigo).matches()) {
            throw new IllegalArgumentException("A regiao deve usar um codigo ISO 3166-2, como BR-PR.");
        }
        return codigo;
    }

    private static String normalizarPlano(String valor) {
        String codigo = textoObrigatorio(valor, "plano", 30).toUpperCase(Locale.ROOT);
        if (!CODIGO_PLANO.matcher(codigo).matches()) {
            throw new IllegalArgumentException("O plano deve usar um codigo estavel em maiusculas e sem espacos.");
        }
        return codigo;
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " e obrigatorio.");
        }
        String normalizado = valor.trim();
        if (normalizado.length() > limite) {
            throw new IllegalArgumentException("O campo " + campo + " excede " + limite + " caracteres.");
        }
        return normalizado;
    }

    private static String textoOpcional(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return textoObrigatorio(valor, campo, limite);
    }

    private static boolean cnpjValido(String valor) {
        if (valor == null || !valor.matches("\\d{14}") || valor.chars().distinct().count() == 1) {
            return false;
        }
        int primeiro = digitoCnpj(valor, 12, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int segundo = digitoCnpj(valor, 13, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return primeiro == Character.digit(valor.charAt(12), 10)
                && segundo == Character.digit(valor.charAt(13), 10);
    }

    private static int digitoCnpj(String valor, int tamanho, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += Character.digit(valor.charAt(i), 10) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
