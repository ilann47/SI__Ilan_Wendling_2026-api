package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "PatioInstalacao")
@Table(name = "patio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatioInstalacao extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;
    @Column(name = "endereco", nullable = false, length = 120)
    private String endereco;
    @Column(name = "numero", length = 10)
    private String numero;
    @Column(name = "complemento", length = 100)
    private String complemento;
    @Column(name = "bairro", nullable = false, length = 60)
    private String bairro;
    @Column(name = "cep", nullable = false, length = 9)
    private String cep;
    @Column(name = "cidade_id", nullable = false)
    private Long cidadeId;
    @Column(name = "capacidade_vagas", nullable = false)
    private Integer capacidadeFisica;
    @Column(name = "capacidade_operacional", nullable = false)
    private Integer capacidadeOperacional;
    @Column(name = "observacao", length = 255)
    private String observacao;
    @Column(name = "ativo", nullable = false)
    private Boolean ativo;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static PatioInstalacao criar(
            Long organizacaoId, String nome, String endereco, String numero, String complemento,
            String bairro, String cep, Long cidadeId, Integer capacidadeFisica,
            Integer capacidadeOperacional, String observacao, Boolean ativo) {
        if (organizacaoId == null || cidadeId == null) {
            throw new IllegalArgumentException("Organizacao e cidade sao obrigatorias.");
        }
        if (capacidadeFisica == null || capacidadeFisica < 1) {
            throw new IllegalArgumentException("A capacidade fisica deve ser de pelo menos uma vaga.");
        }
        if (capacidadeOperacional == null || capacidadeOperacional < 0
                || capacidadeOperacional > capacidadeFisica) {
            throw new IllegalArgumentException(
                    "A capacidade operacional deve estar entre zero e a capacidade fisica.");
        }
        PatioInstalacao patio = new PatioInstalacao();
        patio.organizacaoId = organizacaoId;
        patio.nome = textoObrigatorio(nome, "nome", 120);
        patio.endereco = textoObrigatorio(endereco, "endereco", 120);
        patio.numero = textoOpcional(numero, "numero", 10);
        patio.complemento = textoOpcional(complemento, "complemento", 100);
        patio.bairro = textoObrigatorio(bairro, "bairro", 60);
        patio.cep = textoObrigatorio(cep, "CEP", 9).toUpperCase(Locale.ROOT);
        patio.cidadeId = cidadeId;
        patio.capacidadeFisica = capacidadeFisica;
        patio.capacidadeOperacional = capacidadeOperacional;
        patio.observacao = textoOpcional(observacao, "observacao", 255);
        patio.ativo = ativo == null || ativo;
        return patio;
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new IllegalArgumentException("O campo " + campo + " excede " + limite + " caracteres.");
        }
        return texto;
    }

    private static String textoOpcional(String valor, String campo, int limite) {
        return valor == null || valor.isBlank() ? null : textoObrigatorio(valor, campo, limite);
    }
}
