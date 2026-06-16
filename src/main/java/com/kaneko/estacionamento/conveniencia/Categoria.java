package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Categoria — agrupa produtos do modulo de conveniencia. Mapeia a tabela
 * {@code categoria}.
 *
 * <p>O nome e unico em todo o sistema (restricao {@code uq_categoria_nome}).
 */
@Entity
@Table(name = "categoria")
@Getter
@Setter
public class Categoria extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;
}
