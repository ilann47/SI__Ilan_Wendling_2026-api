package com.kaneko.estacionamento.estoque;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "local_estoque")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalEstoque extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Version
    @Column(nullable = false)
    private Long version;

    public static LocalEstoque criar(Long organizacaoId, String nome) {
        if (organizacaoId == null) {
            throw new IllegalArgumentException("A organizacao e obrigatoria.");
        }
        LocalEstoque local = new LocalEstoque();
        local.organizacaoId = organizacaoId;
        local.nome = normalizarNome(nome);
        return local;
    }

    public void alterar(String nome, Boolean ativo) {
        this.nome = normalizarNome(nome);
        if (ativo != null) {
            this.ativo = ativo;
        }
    }

    public void inativar() {
        this.ativo = false;
    }

    private static String normalizarNome(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O nome do local de estoque e obrigatorio.");
        }
        String nome = valor.trim();
        if (nome.length() > 120) {
            throw new IllegalArgumentException("O nome deve ter no maximo 120 caracteres.");
        }
        return nome;
    }
}
