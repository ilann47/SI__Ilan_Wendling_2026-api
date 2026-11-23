package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.conveniencia.Produto;
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
 * Item de uma {@link NotaEntrada} (detalhe do agregado). Mapeia
 * {@code item_nota_entrada}. Carrega os rateios de frete/seguro/outras despesas
 * e o custo final unitario calculados na criacao da nota.
 */
@Entity
@Table(name = "item_nota_entrada")
@Getter
@Setter
public class ItemNotaEntrada extends AuditableEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_entrada_id", nullable = false)
    private NotaEntrada notaEntrada;

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

    @Column(name = "unidade_medida", length = 10)
    private String unidadeMedida;

    @Column(name = "codigo_produto_fornecedor", length = 60)
    private String codigoProdutoFornecedor;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "custo_total_micros", precision = 19, scale = 0)
    private BigDecimal custoTotal;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "custo_preco_final_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal custoPrecoFinal = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "saldo_estoque_anterior_micros", precision = 18, scale = 0)
    private BigDecimal saldoEstoqueAnterior;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "custo_medio_anterior_micros", precision = 19, scale = 0)
    private BigDecimal custoMedioAnterior;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "custo_medio_posterior_micros", precision = 19, scale = 0)
    private BigDecimal custoMedioPosterior;
}
