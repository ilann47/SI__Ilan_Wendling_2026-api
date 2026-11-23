package com.kaneko.estacionamento.pedido;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "confirmacao_manual_pedido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmacaoManualPedido extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, updatable = false)
    private Pedido pedido;
    @Column(name = "ator_id", nullable = false, updatable = false)
    private Long atorId;
    @Column(name = "meio_pagamento", nullable = false, updatable = false, length = 30)
    private String meioPagamento;
    @Column(name = "referencia_evidencia", nullable = false, updatable = false,
            length = 200)
    private String referenciaEvidencia;
    @Column(nullable = false, updatable = false, length = 300)
    private String motivo;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_conciliacao", nullable = false, length = 30)
    private StatusConciliacaoManual statusConciliacao;
    @Version
    @Column(nullable = false)
    private Long version;

    public static ConfirmacaoManualPedido criar(
            Long organizacaoId, Pedido pedido, Long atorId, String meioPagamento,
            String referenciaEvidencia, String motivo) {
        if (organizacaoId == null || pedido == null || atorId == null
                || !organizacaoId.equals(pedido.getOrganizacaoId())) {
            throw new IllegalArgumentException(
                    "Organizacao, pedido e ator da confirmacao sao obrigatorios.");
        }
        ConfirmacaoManualPedido confirmacao = new ConfirmacaoManualPedido();
        confirmacao.organizacaoId = organizacaoId;
        confirmacao.pedido = pedido;
        confirmacao.atorId = atorId;
        confirmacao.meioPagamento = codigo(meioPagamento, 30);
        confirmacao.referenciaEvidencia = texto(
                referenciaEvidencia, "referencia da evidencia", 200);
        confirmacao.motivo = texto(motivo, "motivo", 300);
        confirmacao.statusConciliacao = StatusConciliacaoManual.PENDENTE_CONCILIACAO;
        return confirmacao;
    }

    private static String codigo(String valor, int limite) {
        String codigo = texto(valor, "meio de pagamento", limite)
                .toUpperCase(Locale.ROOT);
        if (!codigo.matches("[A-Z][A-Z0-9_]{0," + (limite - 1) + "}")) {
            throw new IllegalArgumentException("O meio de pagamento e invalido.");
        }
        return codigo;
    }

    private static String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O " + campo + " e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new IllegalArgumentException(
                    "O " + campo + " excede " + limite + " caracteres.");
        }
        return texto;
    }
}
