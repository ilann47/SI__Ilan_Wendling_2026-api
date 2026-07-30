package com.kaneko.estacionamento.inventario.reserva;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reserva_inventario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservaInventario extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;
    @Column(name = "produto_id", nullable = false, updatable = false)
    private Long produtoId;
    @Column(name = "ator_id", nullable = false, updatable = false)
    private Long atorId;
    @Column(nullable = false, updatable = false)
    private Integer quantidade;
    @Column(nullable = false, updatable = false, length = 30)
    private String canal;
    @Column(name = "referencia_comprador", updatable = false, length = 160)
    private String referenciaComprador;
    @Column(name = "chave_idempotencia", nullable = false, updatable = false, length = 100)
    private String chaveIdempotencia;
    @Column(name = "lote_preco_id", nullable = false, updatable = false)
    private Long lotePrecoId;
    @Column(name = "preco_unitario", nullable = false, updatable = false,
            precision = 19, scale = 2)
    private BigDecimal precoUnitario;
    @Column(nullable = false, updatable = false, length = 3)
    private String moeda;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusReservaInventario status;
    @Column(name = "expira_em", nullable = false, updatable = false)
    private OffsetDateTime expiraEm;
    @Column(name = "finalizada_em")
    private OffsetDateTime finalizadaEm;
    @Column(name = "motivo_finalizacao", length = 300)
    private String motivoFinalizacao;
    @Version
    @Column(nullable = false)
    private Long version;

    public static ReservaInventario criar(
            Long organizacaoId, Long eventoId, Long produtoId, Long atorId,
            Integer quantidade, String canal, String referenciaComprador,
            String chaveIdempotencia, Long lotePrecoId, BigDecimal precoUnitario,
            String moeda, OffsetDateTime expiraEm) {
        if (organizacaoId == null || eventoId == null || produtoId == null
                || atorId == null || lotePrecoId == null) {
            throw new IllegalArgumentException(
                    "Organizacao, evento, produto, ator e lote sao obrigatorios.");
        }
        if (quantidade == null || quantidade < 1) {
            throw new IllegalArgumentException("A quantidade deve ser de pelo menos uma vaga.");
        }
        OffsetDateTime agora = OffsetDateTime.now();
        if (expiraEm == null || !expiraEm.toInstant().isAfter(agora.toInstant())) {
            throw new IllegalArgumentException("A expiracao da reserva deve estar no futuro.");
        }
        ReservaInventario reserva = new ReservaInventario();
        reserva.organizacaoId = organizacaoId;
        reserva.eventoId = eventoId;
        reserva.produtoId = produtoId;
        reserva.atorId = atorId;
        reserva.quantidade = quantidade;
        reserva.canal = codigo(canal, "canal", 30);
        reserva.referenciaComprador = opcional(referenciaComprador, "referencia", 160);
        reserva.chaveIdempotencia = texto(chaveIdempotencia, "chave de idempotencia", 100);
        reserva.lotePrecoId = lotePrecoId;
        reserva.precoUnitario = preco(precoUnitario);
        reserva.moeda = moeda(moeda);
        reserva.expiraEm = expiraEm;
        reserva.status = StatusReservaInventario.MANTIDA;
        return reserva;
    }

    public boolean liberar(String motivo, OffsetDateTime instante) {
        if (status == StatusReservaInventario.LIBERADA) {
            return false;
        }
        if (status != StatusReservaInventario.MANTIDA) {
            throw new IllegalStateException("Somente reserva mantida pode ser liberada.");
        }
        concluir(StatusReservaInventario.LIBERADA, motivo, instante);
        return true;
    }

    public boolean expirar(OffsetDateTime instante) {
        if (status == StatusReservaInventario.EXPIRADA) {
            return false;
        }
        if (status != StatusReservaInventario.MANTIDA) {
            throw new IllegalStateException("Somente reserva mantida pode expirar.");
        }
        if (instante == null || instante.toInstant().isBefore(expiraEm.toInstant())) {
            return false;
        }
        concluir(StatusReservaInventario.EXPIRADA, "PRAZO_EXPIRADO", instante);
        return true;
    }

    private void concluir(
            StatusReservaInventario novoStatus, String motivo, OffsetDateTime instante) {
        if (instante == null) {
            throw new IllegalArgumentException("O instante da transicao e obrigatorio.");
        }
        status = novoStatus;
        motivoFinalizacao = codigo(motivo, "motivo", 300);
        finalizadaEm = instante;
    }

    private static String codigo(String valor, String campo, int limite) {
        String codigo = texto(valor, campo, limite).toUpperCase(Locale.ROOT);
        if (!codigo.matches("[A-Z][A-Z0-9_]{0," + (limite - 1) + "}")) {
            throw new IllegalArgumentException("O " + campo + " e invalido.");
        }
        return codigo;
    }

    private static String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O " + campo + " e obrigatorio.");
        }
        String normalizado = valor.trim();
        if (normalizado.length() > limite) {
            throw new IllegalArgumentException("O " + campo + " excede " + limite + " caracteres.");
        }
        return normalizado;
    }

    private static String opcional(String valor, String campo, int limite) {
        return valor == null ? null : texto(valor, campo, limite);
    }

    private static BigDecimal preco(BigDecimal valor) {
        if (valor == null || valor.signum() < 0 || valor.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("O preco unitario e invalido.");
        }
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String moeda(String valor) {
        String codigo = texto(valor, "moeda", 3).toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(codigo).getCurrencyCode();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A moeda deve ser um codigo ISO 4217 valido.", ex);
        }
    }
}
