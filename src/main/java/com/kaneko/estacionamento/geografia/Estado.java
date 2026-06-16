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
 * Estado (unidade federativa) vinculado a um {@link Pais}. Mapeia a tabela
 * {@code estado}.
 *
 * <p>A combinacao ({@code pais_id}, {@code uf}) e unica no banco
 * (restricao {@code uq_estado_uf_pais}).
 */
@Entity
@Table(name = "estado")
@Getter
@Setter
public class Estado extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    /** Sigla da unidade federativa, ate 3 caracteres (padrao ISO 3166-2; ex.: PR, SP). */
    @Column(name = "uf", nullable = false, length = 3)
    private String uf;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pais_id", nullable = false)
    private Pais pais;
}
