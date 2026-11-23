package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.pessoas.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Veiculo. Mapeia a tabela {@code veiculo}.
 *
 * <p>A placa e unica ({@code uq_veiculo_placa}). O {@code cliente} e opcional:
 * pertence a um cliente quando o veiculo e de mensalista; no avulso, fica nulo.
 */
@Entity
@Table(name = "veiculo")
@Getter
@Setter
public class Veiculo extends BaseEntity {
    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "placa", nullable = false, length = 10)
    private String placa;

    @Column(name = "modelo", length = 60)
    private String modelo;

    @Column(name = "cor", length = 30)
    private String cor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoVeiculo tipo = TipoVeiculo.CARRO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "observacao", length = 255)
    private String observacao;
}
