package com.kaneko.estacionamento.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EventoOperacaoAvulsaTest {
    private Evento evento() {
        var inicio = OffsetDateTime.now().minusHours(1);
        return Evento.criar(1L, 2L, "Festa avulsa", inicio, inicio.plusHours(8), "America/Sao_Paulo", null, null);
    }

    @Test void iniciaSemPublicarProdutoNemAbrirVendas() {
        var evento = evento();
        evento.definirPoliticaReentrada(PoliticaReentrada.ENTRADA_UNICA);
        evento.iniciarOperacaoAvulsa();
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.EM_OPERACAO);
        assertThatThrownBy(evento::iniciarOperacaoAvulsa).isInstanceOf(IllegalStateException.class);
    }

    @Test void exigePoliticaENaoPulaCicloComercialExistente() {
        var evento = evento();
        assertThatThrownBy(evento::iniciarOperacaoAvulsa).isInstanceOf(IllegalStateException.class);
        evento.definirPoliticaReentrada(PoliticaReentrada.ENTRADA_UNICA);
        evento.configurar();
        evento.publicar();
        evento.abrirVendas();
        assertThatThrownBy(evento::iniciarOperacaoAvulsa).isInstanceOf(IllegalStateException.class);
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.VENDAS_ABERTAS);
    }
}
