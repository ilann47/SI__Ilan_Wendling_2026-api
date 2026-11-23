package com.kaneko.estacionamento.logistica;

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
 * Vinculo N:N entre {@link Transportadora} e {@link VeiculoFrota} (a frota da
 * transportadora). Mapeia {@code transportadora_veiculo_frota}.
 *
 * <p>O par e unico no tenant ({@code uq_tvf_organizacao_par}); as duas pontas
 * sao protegidas por {@code fk_tvf_transportadora_organizacao} e
 * {@code fk_tvf_veiculo_organizacao}. O owner e imutavel
 * ({@code ck_tvf_owner_imutavel}).
 */
@Entity
@Table(name = "transportadora_veiculo_frota")
@Getter
@Setter
public class TransportadoraVeiculoFrota extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transportadora_id", nullable = false)
    private Transportadora transportadora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_frota_id", nullable = false)
    private VeiculoFrota veiculoFrota;
}
