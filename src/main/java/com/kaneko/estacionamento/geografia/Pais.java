package com.kaneko.estacionamento.geografia;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Pais — raiz da hierarquia geografica (Pais -> Estado -> Cidade) e base da
 * nacionalidade de pessoas. Mapeia a tabela {@code pais}.
 *
 * <p>O nome e unico em todo o sistema (restricao {@code uq_pais_nome}).
 */
@Entity
@Table(name = "pais")
@Getter
@Setter
public class Pais extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    /** Sigla ISO do pais (ex.: BRA). */
    @Column(name = "sigla", length = 3)
    private String sigla;

    @Column(name = "codigo", length = 5)
    private String codigo;

    /** Gentilico (ex.: Brasileiro). */
    @Column(name = "nacionalidade", length = 100)
    private String nacionalidade;
}
