package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.pagamento.FormaPagamento;
import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Previsão de parcela da nota avulsa, imutável após a confirmação. */
@Embeddable
@Getter
@Setter
public class ParcelaNotaSaida {
    @Column(name = "numero_parcela", nullable = false)
    private int numeroParcela;
    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_micros", nullable = false, precision = 19, scale = 0)
    private BigDecimal valor;
    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pagamento_id")
    private FormaPagamento formaPagamento;
    @Column(name = "forma_pagamento_nome", length = 100)
    private String formaPagamentoNome;
}
