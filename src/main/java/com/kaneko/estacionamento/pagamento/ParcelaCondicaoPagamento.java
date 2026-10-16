package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.common.AuditableEntity;
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
 * Parcela de uma {@link CondicaoPagamento} (detalhe do agregado). Mapeia a tabela
 * {@code parcela_condicao_pagamento}.
 *
 * <p>NAO possui o indicador {@code ativo} (estende {@link AuditableEntity}): seu
 * ciclo de vida acompanha o da condicao raiz (cascade/orphanRemoval). A combinacao
 * ({@code condicao_pagamento_id}, {@code numero}) e unica no banco
 * (restricao {@code uq_parcela_condicao_numero}).
 */
@Entity
@Table(name = "parcela_condicao_pagamento")
@Getter
@Setter
public class ParcelaCondicaoPagamento extends AuditableEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Column(name = "numero", nullable = false)
    private Integer numero;

    /** Dias para vencimento a partir da emissao. */
    @Column(name = "dias", nullable = false)
    private Integer dias;

    /** Percentual do valor total nesta parcela. */
    @Column(name = "percentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentual;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condicao_pagamento_id", nullable = false)
    private CondicaoPagamento condicaoPagamento;

    /** Forma especifica desta parcela (opcional). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pagamento_id")
    private FormaPagamento formaPagamento;
}
