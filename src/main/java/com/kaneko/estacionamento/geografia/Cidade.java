package com.kaneko.estacionamento.geografia;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cidade (municipio) vinculada a um {@link Estado}. Mapeia a tabela
 * {@code cidade}.
 *
 * <p>A combinacao ({@code estado_id}, {@code nome}) e unica no banco
 * (restricao {@code uq_cidade_nome_estado}).
 */
@Entity
@Table(name = "cidade")
@Getter
@Setter
public class Cidade extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    /** Codigo DDD do municipio. */
    @Column(name = "ddd", length = 4)
    private String ddd;

    /** Codigo IBGE do municipio. */
    @Column(name = "codigo_ibge", length = 10)
    private String codigoIbge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_id", nullable = false)
    private Estado estado;
}
