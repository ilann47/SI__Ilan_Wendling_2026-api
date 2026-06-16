package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.conveniencia.Produto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Item de uma {@link NotaEntrada} (detalhe do agregado). Mapeia
 * {@code item_nota_entrada}. Carrega os rateios de frete/seguro/outras despesas
 * e o custo final unitario calculados na criacao da nota.
 */
@Entity
@Table(name = "item_nota_entrada")
@Getter
@Setter
public class ItemNotaEntrada extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_entrada_id", nullable = false)
    private NotaEntrada notaEntrada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "sequencia", nullable = false)
    private Integer sequencia;

    @Column(name = "quantidade", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_desconto", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(name = "percentual_desconto", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualDesconto = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "rateio_frete", nullable = false, precision = 15, scale = 2)
    private BigDecimal rateioFrete = BigDecimal.ZERO;

    @Column(name = "rateio_seguro", nullable = false, precision = 15, scale = 2)
    private BigDecimal rateioSeguro = BigDecimal.ZERO;

    @Column(name = "rateio_outras", nullable = false, precision = 15, scale = 2)
    private BigDecimal rateioOutras = BigDecimal.ZERO;

    @Column(name = "custo_preco_final", nullable = false, precision = 15, scale = 2)
    private BigDecimal custoPrecoFinal = BigDecimal.ZERO;
}
