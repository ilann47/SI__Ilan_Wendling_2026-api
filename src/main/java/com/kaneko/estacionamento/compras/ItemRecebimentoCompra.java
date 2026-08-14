package com.kaneko.estacionamento.compras;

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

@Entity
@Table(name = "item_recebimento_compra")
@Getter
@Setter
public class ItemRecebimentoCompra extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;

    @Column(name = "ordem_compra_id", nullable = false, updatable = false)
    private Long ordemCompraId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recebimento_compra_id", nullable = false, updatable = false)
    private RecebimentoCompra recebimentoCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_ordem_compra_id", nullable = false, updatable = false)
    private ItemOrdemCompra itemOrdemCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;

    @Column(nullable = false, precision = 15, scale = 3, updatable = false)
    private BigDecimal quantidade;

    @Column(name = "custo_unitario", nullable = false, precision = 15, scale = 2,
            updatable = false)
    private BigDecimal custoUnitario;

    @Column(name = "movimento_estoque_id", nullable = false, updatable = false)
    private Long movimentoEstoqueId;
}
