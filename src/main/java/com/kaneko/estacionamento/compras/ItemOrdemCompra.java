package com.kaneko.estacionamento.compras;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.common.exception.ConflitoException;
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
@Table(name = "item_ordem_compra")
@Getter
@Setter
public class ItemOrdemCompra extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_compra_id", nullable = false, updatable = false)
    private OrdemCompra ordemCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private Integer sequencia;

    @Column(name = "quantidade_pedida", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidadePedida;

    @Column(name = "quantidade_recebida", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidadeRecebida = BigDecimal.ZERO;

    @Column(name = "valor_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_desconto", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    public void receber(BigDecimal quantidade) {
        BigDecimal posterior = quantidadeRecebida.add(quantidade);
        if (posterior.compareTo(quantidadePedida) > 0) {
            throw new ConflitoException("A quantidade recebida excede o saldo do item.");
        }
        quantidadeRecebida = posterior;
    }

    public BigDecimal saldoReceber() {
        return quantidadePedida.subtract(quantidadeRecebida);
    }
}
