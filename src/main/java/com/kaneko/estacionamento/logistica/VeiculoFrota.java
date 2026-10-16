package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Veiculo de frota (carga) de uma transportadora. Mapeia {@code veiculo_frota}.
 *
 * <p>Nao confundir com {@code com.kaneko.estacionamento.patio.Veiculo}, que e o
 * carro estacionado pelo cliente. A placa normalizada e unica no tenant
 * ({@code uq_veiculo_frota_organizacao_placa}) e o owner e imutavel
 * ({@code ck_veiculo_frota_owner_imutavel}).
 */
@Entity
@Table(name = "veiculo_frota")
@Getter
@Setter
public class VeiculoFrota extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Column(name = "placa", nullable = false, length = 10)
    private String placa;

    @Column(name = "modelo", length = 50)
    private String modelo;

    @Column(name = "marca", length = 50)
    private String marca;

    @Column(name = "ano")
    private Integer ano;

    @Column(name = "capacidade", precision = 10, scale = 2)
    private BigDecimal capacidade;

    @Column(name = "observacao", length = 255)
    private String observacao;
}
