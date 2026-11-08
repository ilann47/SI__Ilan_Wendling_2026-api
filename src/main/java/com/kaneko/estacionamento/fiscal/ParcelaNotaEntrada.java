package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;

import com.kaneko.estacionamento.pagamento.FormaPagamento;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Cronograma efetivo congelado com a revisão da nota, antes de gerar títulos. */
@Embeddable
@Getter
@Setter
public class ParcelaNotaEntrada {
    @Column(name="numero_parcela", nullable=false)
    private int numeroParcela;
    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valor;
    @Column(name="data_vencimento", nullable=false)
    private LocalDate dataVencimento;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="forma_pagamento_id")
    private FormaPagamento formaPagamento;
    @Column(name="forma_pagamento_nome", length=100)
    private String formaPagamentoNome;
}
