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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cancelamento_pedido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancelamentoPedido extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, updatable = false)
    private Pedido pedido;
    @Column(name = "ator_id", nullable = false, updatable = false)
    private Long atorId;
    @Column(nullable = false, updatable = false, length = 300)
    private String motivo;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_pedido", nullable = false, updatable = false,
            length = 30)
    private StatusPedido statusPedido;
    @Column(name = "reembolso_necessario", nullable = false, updatable = false)
    private Boolean reembolsoNecessario;
    @Column(name = "quantidade_liberada", nullable = false, updatable = false)
    private Integer quantidadeLiberada;
    @Column(name = "credenciais_bloqueadas", nullable = false, updatable = false)
    private Integer credenciaisBloqueadas;
    @Version
    @Column(nullable = false)
    private Long version;

    public static CancelamentoPedido criar(
            Long organizacaoId, Pedido pedido, Long atorId, String motivo,
            boolean reembolsoNecessario, int quantidadeLiberada,
            int credenciaisBloqueadas) {
        if (organizacaoId == null || pedido == null || atorId == null
                || !organizacaoId.equals(pedido.getOrganizacaoId())) {
            throw new IllegalArgumentException(
                    "Organizacao, pedido e ator do cancelamento sao obrigatorios.");
        }
        String motivoNormalizado = texto(motivo);
        if (quantidadeLiberada < 1 || credenciaisBloqueadas < 0) {
            throw new IllegalArgumentException(
                    "Os efeitos do cancelamento sao invalidos.");
        }
        StatusPedido status = pedido.getStatus();
        if ((!reembolsoNecessario && status != StatusPedido.CANCELADO)
                || (reembolsoNecessario && status != StatusPedido.EM_REEMBOLSO)) {
            throw new IllegalArgumentException(
                    "O resultado do cancelamento nao corresponde ao pedido.");
        }
        CancelamentoPedido cancelamento = new CancelamentoPedido();
        cancelamento.organizacaoId = organizacaoId;
        cancelamento.pedido = pedido;
        cancelamento.atorId = atorId;
        cancelamento.motivo = motivoNormalizado;
        cancelamento.statusPedido = status;
        cancelamento.reembolsoNecessario = reembolsoNecessario;
        cancelamento.quantidadeLiberada = quantidadeLiberada;
        cancelamento.credenciaisBloqueadas = credenciaisBloqueadas;
        return cancelamento;
    }

    private static String texto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "O motivo do cancelamento e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > 300) {
            throw new IllegalArgumentException(
                    "O motivo do cancelamento excede 300 caracteres.");
        }
        return texto;
    }
}
