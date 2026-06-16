package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.BaseEntity;
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

    @Column(name = "valor_compra", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorCompra = BigDecimal.ZERO;

    @Column(name = "valor_venda", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorVenda = BigDecimal.ZERO;

    @Column(name = "percentual_lucro", nullable = false, precision = 10, scale = 2)
    private BigDecimal percentualLucro = BigDecimal.ZERO;

    @Column(name = "quantidade", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade = BigDecimal.ZERO;

    @Column(name = "quantidade_minima", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidadeMinima = BigDecimal.ONE;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "observacao", length = 255)
    private String observacao;
}
