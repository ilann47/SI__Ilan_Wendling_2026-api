package com.kaneko.estacionamento.fiscal.importacao;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.conveniencia.Produto;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="item_importacao_nota_entrada") @Getter @Setter
public class ItemImportacaoNotaEntrada extends AuditableEntity {
    @Column(name="organizacao_id", nullable=false, updatable=false) private Long organizacaoId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="importacao_id", nullable=false)
    private ImportacaoNotaEntrada importacao;
    @Column(nullable=false) private Integer sequencia;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="produto_id", nullable=false) private Produto produto;
    @Column(name="unidade_destino", nullable=false, length=10) private String unidadeDestino;
    @Column(name="fator_conversao", nullable=false, precision=18, scale=6) private BigDecimal fatorConversao;
    @Column(nullable=false, precision=15, scale=3) private BigDecimal quantidade;
    @Column(name="valor_unitario", nullable=false, precision=15, scale=2) private BigDecimal valorUnitario;
    @Column(name="quantidade_conferida", precision=15, scale=3) private BigDecimal quantidadeConferida;
    @Column(name="ordem_compra_id") private Long ordemCompraId;
    @Column(name="item_ordem_compra_id") private Long itemOrdemCompraId;
    @Column(name="motivo_divergencia", length=300) private String motivoDivergencia;
}
