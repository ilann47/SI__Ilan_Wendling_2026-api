package com.kaneko.estacionamento.fiscal.importacao;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;

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
    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "fator_conversao_micros", nullable = false, precision = 18, scale = 0)
    private BigDecimal fatorConversao;
    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "quantidade_micros", nullable = false, precision = 18, scale = 0)
    private BigDecimal quantidade;
    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_unitario_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valorUnitario;
    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "quantidade_conferida_micros", precision = 18, scale = 0)
    private BigDecimal quantidadeConferida;
    @Column(name="motivo_divergencia", length=300) private String motivoDivergencia;
}
