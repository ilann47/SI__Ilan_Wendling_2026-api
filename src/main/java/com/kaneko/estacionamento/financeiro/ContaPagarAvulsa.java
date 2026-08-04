package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.pagamento.FormaPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Despesa avulsa a pagar (sem nota fiscal de entrada): aluguel, luz, agua,
 * salario, etc. Mapeia {@code contas_pagar_avulsa}.
 *
 * <p>Entidade transacional (sem 'ativo') — estende {@link AuditableEntity}.
 */
@Entity
@Table(name = "contas_pagar_avulsa")
@Getter
@Setter
public class ContaPagarAvulsa extends AuditableEntity {

    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "descricao", nullable = false, length = 255)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pagamento_id")
    private FormaPagamento formaPagamento;

    @Column(name = "numero_nota", length = 20)
    private String numeroNota;

    @Column(name = "modelo", length = 10)
    private String modelo;

    @Column(name = "serie", length = 10)
    private String serie;

    @Column(name = "numero_parcela", nullable = false)
    private Integer numeroParcela = 1;

    @Column(name = "total_parcelas", nullable = false)
    private Integer totalParcelas = 1;

    @Column(name = "valor_original", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorOriginal;

    @Column(name = "valor_pago", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorPago = BigDecimal.ZERO;

    @Column(name = "valor_desconto", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(name = "valor_juros", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorJuros = BigDecimal.ZERO;

    @Column(name = "valor_multa", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorMulta = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoContaPagar situacao = SituacaoContaPagar.PENDENTE;

    @Column(name = "observacao", length = 255)
    private String observacao;
}
