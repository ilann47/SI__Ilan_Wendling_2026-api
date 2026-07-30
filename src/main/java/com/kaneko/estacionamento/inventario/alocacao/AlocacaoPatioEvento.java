package com.kaneko.estacionamento.inventario.alocacao;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "alocacao_patio_evento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlocacaoPatioEvento extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;
    @Column(name = "patio_id", nullable = false, updatable = false)
    private Long patioId;
    @Column(nullable = false, updatable = false)
    private OffsetDateTime inicio;
    @Column(nullable = false, updatable = false)
    private OffsetDateTime fim;
    @Column(name = "capacidade_operacional", nullable = false)
    private Integer capacidadeOperacional;
    @Column(name = "capacidade_vendavel", nullable = false)
    private Integer capacidadeVendavel;
    @Column(name = "capacidade_reservada", nullable = false)
    private Integer capacidadeReservada;
    @Version
    @Column(nullable = false)
    private Long version;

    public static AlocacaoPatioEvento criar(
            Long organizacaoId, Long eventoId, Long patioId,
            OffsetDateTime inicio, OffsetDateTime fim,
            Integer capacidadeOperacional, Integer capacidadeVendavel,
            Integer capacidadeReservada) {
        if (organizacaoId == null || eventoId == null || patioId == null) {
            throw new IllegalArgumentException("Organizacao, evento e patio sao obrigatorios.");
        }
        validarDados(inicio, fim, capacidadeOperacional, capacidadeVendavel, capacidadeReservada);
        var alocacao = new AlocacaoPatioEvento();
        alocacao.organizacaoId = organizacaoId;
        alocacao.eventoId = eventoId;
        alocacao.patioId = patioId;
        alocacao.inicio = inicio;
        alocacao.fim = fim;
        alocacao.capacidadeOperacional = capacidadeOperacional;
        alocacao.capacidadeVendavel = capacidadeVendavel;
        alocacao.capacidadeReservada = capacidadeReservada;
        return alocacao;
    }

    public void alterarCapacidades(
            Integer capacidadeOperacional, Integer capacidadeVendavel,
            Integer capacidadeReservada) {
        validarCapacidades(capacidadeOperacional, capacidadeVendavel, capacidadeReservada);
        this.capacidadeOperacional = capacidadeOperacional;
        this.capacidadeVendavel = capacidadeVendavel;
        this.capacidadeReservada = capacidadeReservada;
    }

    static void validarDados(
            OffsetDateTime inicio, OffsetDateTime fim, Integer capacidadeOperacional,
            Integer capacidadeVendavel, Integer capacidadeReservada) {
        validarPeriodo(inicio, fim);
        validarCapacidades(capacidadeOperacional, capacidadeVendavel, capacidadeReservada);
    }

    private static void validarPeriodo(OffsetDateTime inicio, OffsetDateTime fim) {
        if (inicio == null || fim == null || !fim.toInstant().isAfter(inicio.toInstant())) {
            throw new IllegalArgumentException("O fim da alocacao deve ser posterior ao inicio.");
        }
    }

    private static void validarCapacidades(
            Integer operacional, Integer vendavel, Integer reservada) {
        if (operacional == null || operacional < 1 || vendavel == null || vendavel < 0
                || reservada == null || reservada < 0 || vendavel + reservada > operacional) {
            throw new IllegalArgumentException(
                    "As capacidades devem respeitar vendavel + reservada <= operacional e valores nao negativos.");
        }
    }
}
