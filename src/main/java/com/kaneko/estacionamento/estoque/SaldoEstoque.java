package com.kaneko.estacionamento.estoque;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saldo_estoque")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaldoEstoque extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;

    @Column(name = "produto_id", nullable = false, updatable = false)
    private Long produtoId;

    @Column(name = "local_estoque_id", nullable = false, updatable = false)
    private Long localEstoqueId;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Long version;

    public BigDecimal aplicar(BigDecimal delta) {
        BigDecimal posterior = quantidade.add(delta);
        if (posterior.signum() < 0) {
            throw new ConflitoException("O movimento deixaria o saldo de estoque negativo.");
        }
        quantidade = posterior;
        return posterior;
    }
}
