package com.kaneko.estacionamento.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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

    @Test
    void configuraEPublicaSomenteNaOrdemAprovada() {
        Evento evento = novoEvento();

        assertThatIllegalStateException().isThrownBy(evento::publicar);

        evento.definirPoliticaReentrada(PoliticaReentrada.ENTRADA_UNICA);
        evento.configurar();
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.CONFIGURADO);
        assertThatIllegalStateException().isThrownBy(evento::configurar);

        evento.publicar();
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.PUBLICADO);
        assertThatIllegalStateException().isThrownBy(evento::publicar);

        evento.abrirVendas();
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.VENDAS_ABERTAS);
        assertThatIllegalStateException().isThrownBy(evento::abrirVendas);
    }

    @Test
    void encerraVendasEIniciaOperacaoSomenteNaOrdemAprovada() {
        Evento evento = novoEvento();
        evento.definirPoliticaReentrada(PoliticaReentrada.ENTRADA_UNICA);
        evento.configurar();
        evento.publicar();
        evento.abrirVendas();

        assertThatIllegalStateException().isThrownBy(evento::iniciarOperacao);

        evento.encerrarVendas();
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.VENDAS_ENCERRADAS);
        assertThatIllegalStateException().isThrownBy(evento::encerrarVendas);

        evento.iniciarOperacao();
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.EM_OPERACAO);
        assertThatIllegalStateException().isThrownBy(evento::iniciarOperacao);
    }

    @Test
    void exigePoliticaExplicitaEImpedeAlteracaoDepoisDaPublicacao() {
        Evento evento = novoEvento();

        assertThatIllegalStateException().isThrownBy(evento::configurar)
                .withMessageContaining("reentrada");

        evento.definirPoliticaReentrada(PoliticaReentrada.REENTRADA_PERMITIDA);
        assertThat(evento.getPoliticaReentrada())
                .isEqualTo(PoliticaReentrada.REENTRADA_PERMITIDA);
        evento.configurar();
        evento.publicar();

        assertThatIllegalStateException().isThrownBy(() ->
                evento.definirPoliticaReentrada(PoliticaReentrada.ENTRADA_UNICA));
    }

    private static Evento novoEvento() {
        return Evento.criar(
                1L, 2L, "Evento Publicavel",
                OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                OffsetDateTime.parse("2026-10-10T23:30:00-03:00"),
                "America/Sao_Paulo", null);
    }
}
