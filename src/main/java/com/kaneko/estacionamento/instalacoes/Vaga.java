package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vaga")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vaga extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "setor_id", nullable = false, updatable = false)
    private Long setorId;
    @Column(nullable = false, length = 40)
    private String codigo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaSetor tipo;
    @Column(nullable = false)
    private Boolean acessivel;
    @Column(length = 120)
    private String posicao;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVaga status;
    @Version
    @Column(nullable = false)
    private Long version;

    public static Vaga criar(
            Long organizacaoId, Long setorId, String codigo,
            CategoriaSetor tipo, Boolean acessivel, String posicao) {
        if (organizacaoId == null || setorId == null || tipo == null || acessivel == null) {
            throw new IllegalArgumentException(
                    "Organizacao, setor, tipo e acessibilidade sao obrigatorios.");
        }
        Vaga vaga = new Vaga();
        vaga.organizacaoId = organizacaoId;
        vaga.setorId = setorId;
        vaga.codigo = Setor.normalizarCodigo(codigo);
        vaga.tipo = tipo;
        vaga.acessivel = acessivel;
        vaga.posicao = normalizarPosicao(posicao);
        vaga.status = StatusVaga.LIVRE;
        return vaga;
    }

    public boolean equivale(
            CategoriaSetor tipo, Boolean acessivel, String posicao) {
        return this.tipo == tipo
                && this.acessivel.equals(acessivel)
                && java.util.Objects.equals(this.posicao, normalizarPosicao(posicao));
    }

    static String normalizarPosicao(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String posicao = valor.trim();
        if (posicao.length() > 120) {
            throw new IllegalArgumentException("A posicao excede 120 caracteres.");
        }
        return posicao;
    }
}
