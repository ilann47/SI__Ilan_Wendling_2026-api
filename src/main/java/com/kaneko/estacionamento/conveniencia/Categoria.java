package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Categoria — agrupa produtos do modulo de conveniencia. Mapeia a tabela
 * {@code categoria}.
 *
 * <p>O nome e unico dentro da organizacao proprietaria. Registros historicos
 * podem permanecer sem owner ate a reconciliacao explicita.
 */
@Entity
@Table(name = "categoria")
@Getter
@Setter
public class Categoria extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;
}
