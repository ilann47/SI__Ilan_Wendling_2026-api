package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Item de uma {@link NotaSaida} (detalhe do agregado). Mapeia
 * {@code item_nota_saida}.
 */
@Entity
@Table(name = "item_nota_saida")
@Getter
@Setter
public class ItemNotaSaida extends AuditableEntity {
    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_saida_id", nullable = false)
    private NotaSaida notaSaida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "sequencia", nullable = false)
    private Integer sequencia;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "quantidade_micros", nullable = false, precision = 18, scale = 0)
    private BigDecimal quantidade;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_unitario_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorUnitario;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_desconto_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "percentual_desconto_micros", nullable = false, precision = 9, scale = 0)
    private BigDecimal percentualDesconto = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_total_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorTotal;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "rateio_frete_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal rateioFrete = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "rateio_seguro_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal rateioSeguro = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "rateio_outras_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal rateioOutras = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "custo_preco_final_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal custoPrecoFinal = BigDecimal.ZERO;
}
