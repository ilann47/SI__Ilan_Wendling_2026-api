package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Vinculo N:N entre {@link Produto} e {@link Fornecedor}, com o codigo do
 * produto no fornecedor e o custo de aquisicao. Mapeia {@code produto_fornecedor}.
 *
 * <p>O par (produto, fornecedor) e unico ({@code uq_produto_fornecedor}).
 */
@Entity
@Table(name = "produto_fornecedor")
@Getter
@Setter
public class ProdutoFornecedor extends BaseEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @Column(name = "codigo_prod", length = 50)
    private String codigoProd;

    @Column(name = "custo", precision = 15, scale = 2)
    private BigDecimal custo;
}
