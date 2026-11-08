package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.Setter;

/**
 * Produto da conveniencia, com controle de estoque. Mapeia {@code produto}.
 *
 * <p>Marca e unidade de medida sao obrigatorias; categoria e opcional. O codigo
 * de barras, quando informado, e unico ({@code uq_produto_codigo_barras}).
 */
@Entity
@Table(name = "produto")
@Getter
@Setter
public class Produto extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "codigo_barras", length = 20)
    private String codigoBarras;

    @Column(name = "referencia", length = 10)
    private String referencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_medida_id", nullable = false)
    private UnidadeMedida unidadeMedida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_compra_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorCompra = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "custo_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal custo = BigDecimal.ZERO;

    @Column(name = "valor_venda_micros", nullable = false, precision = 19, scale = 0)
    @Convert(converter = MilionesimosConverter.class)
    private BigDecimal valorVenda = BigDecimal.ZERO;


    // Preço pode chegar a 13 dígitos inteiros; custo pode ser 0,000001.
    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "percentual_lucro_micros", nullable = false, precision = 27, scale = 0)
    private BigDecimal percentualLucro = BigDecimal.ZERO;

    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "quantidade_micros", nullable = false, precision = 18, scale = 0)
    private BigDecimal quantidade = BigDecimal.ZERO;

    @Column(name = "quantidade_minima_micros", nullable = false, precision = 18, scale = 0)
    @Convert(converter = MilionesimosConverter.class)
    private BigDecimal quantidadeMinima = BigDecimal.ONE;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "observacao", length = 255)
    private String observacao;

    /**
     * Recalcula a margem de lucro a partir do custo e do valor de venda:
     * {@code lucro% = (venda - custo) / custo * 100}. Fica zero enquanto nao
     * houver custo (produto ainda sem entrada).
     */
    public void recalcularLucro() {
        BigDecimal venda = getValorVenda();
        if (custo != null && custo.signum() > 0 && venda != null) {
            this.percentualLucro = venda.subtract(custo)
                    .multiply(new BigDecimal("100"))
                    .divide(custo, MilionesimosConverter.ESCALA, RoundingMode.HALF_UP);
        } else {
            this.percentualLucro = BigDecimal.ZERO;
        }
    }
}
