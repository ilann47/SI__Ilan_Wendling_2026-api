package com.kaneko.estacionamento.inventario.alocacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapacidadeTemporalTest {
    @Test
    void recortaJanelaEConsolidaSaldoContinuoComOffsetsEquivalentes() {
        var inicio = data("2026-11-22T09:00:00-03:00");
        var fim = data("2026-11-22T15:00:00Z");
        var alocacoes = List.of(
                alocar("2026-11-22T08:00:00-03:00", "2026-11-22T13:00:00Z", 10),
                alocar("2026-11-22T10:00:00-03:00", "2026-11-22T14:00:00Z", 10),
                alocar("2026-11-22T15:00:00Z", "2026-11-22T16:00:00Z", 20));

        var saldo = CapacidadeTemporal.calcular(10, inicio, fim, alocacoes, true);

        assertThat(saldo.availableCapacity()).isZero();
        assertThat(saldo.segments()).containsExactly(
                new CapacidadeTemporal.Segmento(inicio.toInstant(), Instant.parse("2026-11-22T14:00:00Z"), 0),
                new CapacidadeTemporal.Segmento(Instant.parse("2026-11-22T14:00:00Z"), fim.toInstant(), 10));
        assertThat(CapacidadeTemporal.calcular(10, inicio, fim, alocacoes, false).segments()).isEmpty();
    }

    @Test
    void janelaVaziaTemTodaCapacidadeEUmaFaixa() {
        var inicio = data("2026-11-22T00:00:00Z");
        var fim = data("2026-11-23T00:00:00Z");

        var saldo = CapacidadeTemporal.calcular(17, inicio, fim, List.of(), true);

        assertThat(saldo.availableCapacity()).isEqualTo(17);
        assertThat(saldo.segments()).containsExactly(
                new CapacidadeTemporal.Segmento(inicio.toInstant(), fim.toInstant(), 17));
    }

    private static AlocacaoPatioEvento alocar(String inicio, String fim, int capacidade) {
        return AlocacaoPatioEvento.criar(1L, 2L, 3L, data(inicio), data(fim), capacidade, 0, 0);
    }

    private static OffsetDateTime data(String valor) {
        return OffsetDateTime.parse(valor);
    }
}
