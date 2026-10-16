package com.kaneko.estacionamento.estoque;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "movimento_estoque")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;

    @Column(name = "produto_id", nullable = false, updatable = false)
    private Long produtoId;

    @Column(name = "local_estoque_id", nullable = false, updatable = false)
    private Long localEstoqueId;

    @Column(name = "saldo_estoque_id", nullable = false, updatable = false)
    private Long saldoEstoqueId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TipoMovimentoEstoque tipo;

    @Column(nullable = false, precision = 15, scale = 3, updatable = false)
    private BigDecimal delta;

    @Column(name = "saldo_anterior", nullable = false, precision = 15, scale = 3,
            updatable = false)
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_posterior", nullable = false, precision = 15, scale = 3,
            updatable = false)
    private BigDecimal saldoPosterior;

    @Column(name = "custo_unitario", precision = 15, scale = 2, updatable = false)
    private BigDecimal custoUnitario;

    @Column(name = "origem_tipo", nullable = false, length = 50, updatable = false)
    private String origemTipo;

    @Column(name = "origem_chave", nullable = false, length = 160, updatable = false)
    private String origemChave;

    @Column(name = "compensado_id", updatable = false)
    private Long compensadoId;

    @Column(name = "ator_id", nullable = false, updatable = false)
    private Long atorId;

    @Column(nullable = false, length = 500, updatable = false)
    private String motivo;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private OffsetDateTime ocorridoEm;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    static MovimentoEstoque registrar(Long organizacaoId, Long produtoId, Long localId,
            Long saldoId, TipoMovimentoEstoque tipo, BigDecimal delta,
            BigDecimal saldoAnterior, BigDecimal saldoPosterior, BigDecimal custoUnitario,
            String origemTipo, String origemChave, Long compensadoId, Long atorId,
            String motivo) {
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.organizacaoId = organizacaoId;
        movimento.produtoId = produtoId;
        movimento.localEstoqueId = localId;
        movimento.saldoEstoqueId = saldoId;
        movimento.tipo = tipo;
        movimento.delta = delta;
        movimento.saldoAnterior = saldoAnterior;
        movimento.saldoPosterior = saldoPosterior;
        movimento.custoUnitario = custoUnitario;
        movimento.origemTipo = origemTipo;
        movimento.origemChave = origemChave;
        movimento.compensadoId = compensadoId;
        movimento.atorId = atorId;
        movimento.motivo = motivo.trim();
        movimento.ocorridoEm = OffsetDateTime.now();
        return movimento;
    }
}
