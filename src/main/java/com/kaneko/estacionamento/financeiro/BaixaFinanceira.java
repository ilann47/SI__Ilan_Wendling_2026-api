package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.common.util.MilionesimosConverter;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

@Entity @Table(name = "baixa_financeira") @Immutable @Getter @Setter
public class BaixaFinanceira {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "organizacao_id", updatable = false) private Long organizacaoId;
    @Column(name = "conta_pagar_id", updatable = false) private Long contaPagarId;
    @Column(name = "conta_receber_id", updatable = false) private Long contaReceberId;
    @Convert(converter = MilionesimosConverter.class)
    @Column(name = "valor_micros", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal valor;
    @Column(updatable = false) private LocalDate data;
    @Column(name = "ator_id", updatable = false) private Long atorId;
    @Column(name = "ator_nome", length = 150, updatable = false) private String atorNome;
    @Column(nullable = false, length = 160, updatable = false) private String chave;
    @Column(name = "request_hash", length = 64, updatable = false) private String requestHash;
    @Column(name = "saldo_anterior", nullable = false, updatable = false) private boolean saldoAnterior;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
}
