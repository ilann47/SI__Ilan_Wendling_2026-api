package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.common.BaseEntity;
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
 * <p>O par (transportadora, veiculo) e unico ({@code uq_transp_veiculo_frota}).
 */
@Entity
@Table(name = "transportadora_veiculo_frota")
@Getter
@Setter
public class TransportadoraVeiculoFrota extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transportadora_id", nullable = false)
    private Transportadora transportadora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_frota_id", nullable = false)
    private VeiculoFrota veiculoFrota;
}
