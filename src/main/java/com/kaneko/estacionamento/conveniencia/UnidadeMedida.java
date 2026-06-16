package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * UnidadeMedida — unidade usada para quantificar produtos de conveniencia
 * (ex.: Unidade, Quilograma, Litro). Mapeia a tabela {@code unidade_medida}.
 *
 * <p>A sigla e unica em todo o sistema (restricao {@code uq_unidade_sigla}).
 */
@Entity
@Table(name = "unidade_medida")
@Getter
@Setter
public class UnidadeMedida extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    /** Sigla da unidade (ex.: UN, KG, L, PCT). Unica. */
    @Column(name = "sigla", nullable = false, length = 6)
    private String sigla;
}
