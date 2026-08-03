package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "setor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setor extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "patio_id", nullable = false, updatable = false)
    private Long patioId;
    @Column(nullable = false, length = 40)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String nome;
    @Column(nullable = false)
    private Integer capacidade;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "varchar(20)[]")
    private String[] categorias;
    @Column(nullable = false)
    private Boolean ativo;
    @Version
    @Column(nullable = false)
    private Long version;

    public static Setor criar(
            Long organizacaoId, Long patioId, String codigo, String nome,
            Integer capacidade, Set<CategoriaSetor> categorias) {
        if (organizacaoId == null || patioId == null) {
            throw new IllegalArgumentException("Organizacao e patio sao obrigatorios.");
        }
        if (capacidade == null || capacidade < 1) {
            throw new IllegalArgumentException("A capacidade do setor deve ser positiva.");
        }
        if (categorias == null || categorias.isEmpty()
                || categorias.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("O setor deve possuir ao menos uma categoria valida.");
        }
        Setor setor = new Setor();
        setor.organizacaoId = organizacaoId;
        setor.patioId = patioId;
        setor.codigo = normalizarCodigo(codigo);
        setor.nome = textoObrigatorio(nome, "nome", 120);
        setor.capacidade = capacidade;
        setor.categorias = categorias.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .toArray(String[]::new);
        setor.ativo = true;
        return setor;
    }

    public List<CategoriaSetor> categorias() {
        return Arrays.stream(categorias).map(CategoriaSetor::valueOf).toList();
    }

    static String normalizarCodigo(String valor) {
        String codigo = textoObrigatorio(valor, "codigo", 40).toUpperCase(Locale.ROOT);
        if (!codigo.matches("[A-Z0-9][A-Z0-9_-]{0,39}")) {
            throw new IllegalArgumentException("O codigo do setor e invalido.");
        }
        return codigo;
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new IllegalArgumentException(
                    "O campo " + campo + " excede " + limite + " caracteres.");
        }
        return texto;
    }
}
