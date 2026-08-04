package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.AuditableEntity;
import com.kaneko.estacionamento.conveniencia.Servico;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.FormaPagamento;
import com.kaneko.estacionamento.patio.Mensalista;
import com.kaneko.estacionamento.patio.Movimentacao;
import com.kaneko.estacionamento.pessoas.Cliente;
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
 * Nota fiscal de servico (NFS-e) do estacionamento/lavagem, com destaque de ISS.
 * Mapeia {@code nota_servico}.
 *
 * <p>A origem do servico pode ser uma {@link Movimentacao} (avulso) ou um
 * {@link Mensalista} (mensalidade), nunca ambos ao mesmo tempo
 * ({@code ck_nota_servico_origem}).
 */
@Entity
@Table(name = "nota_servico")
@Getter
@Setter
public class NotaServico extends AuditableEntity {
    @Column(name = "organizacao_id", updatable = false)
    private Long organizacaoId;

    @Version @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "numero", nullable = false, length = 20)
    private String numero;

    @Column(name = "modelo", nullable = false, length = 10)
    private String modelo = "55";

    @Column(name = "serie", nullable = false, length = 10)
    private String serie = "1";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimentacao_id")
    private Movimentacao movimentacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mensalista_id")
    private Mensalista mensalista;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condicao_pagamento_id")
    private CondicaoPagamento condicaoPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pagamento_id")
    private FormaPagamento formaPagamento;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "valor_servico", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorServico = BigDecimal.ZERO;

    @Column(name = "aliquota_iss", nullable = false, precision = 5, scale = 2)
    private BigDecimal aliquotaIss = BigDecimal.ZERO;

    @Column(name = "valor_iss", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorIss = BigDecimal.ZERO;

    @Column(name = "valor_desconto", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    private SituacaoNotaServico situacao = SituacaoNotaServico.PENDENTE;

    @Column(name = "observacao", length = 255)
    private String observacao;
}
