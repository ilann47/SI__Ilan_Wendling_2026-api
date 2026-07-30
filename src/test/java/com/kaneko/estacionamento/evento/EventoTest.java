package com.kaneko.estacionamento.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EventoTest {

    @Test
    void criaRascunhoNormalizandoCampos() {
        Evento evento = Evento.criar(
                1L, 2L, "  Final do Campeonato  ",
                OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                OffsetDateTime.parse("2026-10-10T23:30:00-03:00"),
                "America/Sao_Paulo", "  parceiro-123  ");

        assertThat(evento.getNome()).isEqualTo("Final do Campeonato");
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.RASCUNHO);
        assertThat(evento.getFusoHorario()).isEqualTo("America/Sao_Paulo");
        assertThat(evento.getExternalId()).isEqualTo("parceiro-123");
    }

    @Test
    void rejeitaPeriodoETimezoneInvalidos() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T19:00:00-03:00");

        assertThatThrownBy(() -> Evento.criar(
                1L, 2L, "Evento", inicio, inicio, "America/Sao_Paulo", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
        assertThatThrownBy(() -> Evento.criar(
                1L, 2L, "Evento", inicio, inicio.plusHours(1), "Sao_Paulo", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IANA");
    }
}
