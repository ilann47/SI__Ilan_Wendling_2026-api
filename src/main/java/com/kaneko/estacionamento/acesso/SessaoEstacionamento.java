package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sessao_estacionamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessaoEstacionamento extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;
    @Column(name = "patio_id", nullable = false, updatable = false)
    private Long patioId;
    @Column(name = "credencial_id", nullable = false, updatable = false)
    private Long credencialId;
    @Column(name = "pedido_id", nullable = false, updatable = false)
    private Long pedidoId;
    @Column(name = "item_pedido_id", nullable = false, updatable = false)
    private Long itemPedidoId;
    @Column(name = "presenca_id", nullable = false, updatable = false)
    private Long presencaId;
    @Column(name = "tentativa_entrada_id", nullable = false, updatable = false)
    private Long tentativaEntradaId;
    @Column(name = "origem_direito", nullable = false, updatable = false, length = 30)
    private String origemDireito;
    @Column(name = "placa_veiculo", updatable = false, length = 7)
    private String placaVeiculo;
    @Column(name = "entrada_em", nullable = false, updatable = false)
    private OffsetDateTime entradaEm;
    @Column(name = "saida_em")
    private OffsetDateTime saidaEm;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSessaoEstacionamento status;
    @Version
    @Column(nullable = false)
    private Long version;

    public static SessaoEstacionamento abrir(
            Long organizacaoId, Long eventoId, Long patioId,
            Long credencialId, Long pedidoId, Long itemPedidoId,
            Long presencaId, Long tentativaEntradaId, String placaVeiculo,
            OffsetDateTime instante) {
        if (organizacaoId == null || eventoId == null || patioId == null
                || credencialId == null || pedidoId == null || itemPedidoId == null
                || presencaId == null || tentativaEntradaId == null
                || instante == null) {
            throw new IllegalArgumentException(
                    "O escopo e a origem da sessao sao obrigatorios.");
        }
        SessaoEstacionamento sessao = new SessaoEstacionamento();
        sessao.organizacaoId = organizacaoId;
        sessao.eventoId = eventoId;
        sessao.patioId = patioId;
        sessao.credencialId = credencialId;
        sessao.pedidoId = pedidoId;
        sessao.itemPedidoId = itemPedidoId;
        sessao.presencaId = presencaId;
        sessao.tentativaEntradaId = tentativaEntradaId;
        sessao.origemDireito = "PEDIDO_EVENTO";
        sessao.placaVeiculo = placaVeiculo;
        sessao.entradaEm = instante;
        sessao.status = StatusSessaoEstacionamento.ABERTA;
        return sessao;
    }
}
