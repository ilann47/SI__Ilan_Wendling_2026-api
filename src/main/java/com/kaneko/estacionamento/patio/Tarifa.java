package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Tarifa do estacionamento. Mapeia a tabela {@code tarifa}.
 *
 * <p>Define o valor por hora, o teto diario ({@code valor_diaria} — o avulso
 * nunca paga mais que isso por dia), a tolerancia de cortesia em minutos e o
 * valor padrao da mensalidade. Os valores nao-negativos sao garantidos no
 * banco pela restricao {@code ck_tarifa_valores}.
 */
@Entity
@Table(name = "tarifa")
@Getter
@Setter
public class Tarifa extends BaseEntity {

    @Column(name = "descricao", nullable = false, length = 80)
    private String descricao;

    @Column(name = "valor_hora", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorHora;

    @Column(name = "valor_diaria", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorDiaria;

    @Column(name = "tolerancia_min", nullable = false)
    private Integer toleranciaMin = 15;

    @Column(name = "valor_mensalidade", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorMensalidade;
}
