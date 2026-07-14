package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Movimentacao de patio — o "ticket" de entrada/saida. Mapeia a tabela
 * {@code movimentacao}.
 *
 * <p>Coerencia (ck_mov_tipo_coerencia): no tipo MENSALISTA o {@code mensalista}
 * e obrigatorio; no AVULSO e nulo (e usa-se a {@code tarifa} aplicada). O banco
 * garante no maximo uma movimentacao ABERTA por veiculo
 * ({@code uq_mov_veiculo_aberta}).
 */
@Entity
@Table(name = "movimentacao")
@Getter
@Setter
public class Movimentacao extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mensalista_id")
    private Mensalista mensalista;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarifa_id")
    private Tarifa tarifa;

    /** Patio onde a movimentacao ocorre (base da ocupacao por patio). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patio_id")
    private Patio patio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoMovimentacao tipo;

    @Column(name = "data_entrada", nullable = false)
    private OffsetDateTime dataEntrada;

    @Column(name = "data_saida")
    private OffsetDateTime dataSaida;

    @Column(name = "valor_cobrado", precision = 10, scale = 2)
    private BigDecimal valorCobrado;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusMovimentacao status = StatusMovimentacao.ABERTO;
}
