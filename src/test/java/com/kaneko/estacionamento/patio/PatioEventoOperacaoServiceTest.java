package com.kaneko.estacionamento.patio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.evento.Evento;
import com.kaneko.estacionamento.evento.EventoRepository;
import com.kaneko.estacionamento.evento.PoliticaReentrada;
import com.kaneko.estacionamento.evento.StatusEvento;
import com.kaneko.estacionamento.evento.VersaoEventoDivergenteException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

class PatioEventoOperacaoServiceTest {
    final EventoRepository eventos = mock(EventoRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AuditoriaService auditoria = mock(AuditoriaService.class);
    final OutboxService outbox = mock(OutboxService.class);
    final PatioEventoOperacaoService service = new PatioEventoOperacaoService(eventos, jdbc, auditoria, outbox);
    Evento evento;

    @BeforeEach void setup() {
        var agora = OffsetDateTime.now();
        evento = Evento.criar(7L, 2L, "Festa", agora.minusHours(1), agora.plusHours(3), "America/Sao_Paulo", null, null);
        evento.definirPoliticaReentrada(PoliticaReentrada.ENTRADA_UNICA);
        ReflectionTestUtils.setField(evento, "id", 8L);
        ReflectionTestUtils.setField(evento, "version", 2L);
        when(eventos.findByIdAndOrganizacaoIdForUpdate(8L, 7L)).thenReturn(Optional.of(evento));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(true);
    }

    @Test void iniciaSomenteAvulsoComContratoVersionadoEAuditoria() {
        var resposta = service.iniciar(7, 8, 2);
        assertThat(resposta.status()).isEqualTo(StatusEvento.EM_OPERACAO);
        assertThat(resposta.configurationChecklist().parkingProduct()).isFalse();
        verify(auditoria).registrar(any());
        verify(outbox).publicar(any());
    }

    @Test void naoConsultaOuMudaEventoDeOutroTenant() {
        assertThatThrownBy(() -> service.iniciar(99, 8, 2)).isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(jdbc, auditoria, outbox);
    }

    @Test void versaoDivergenteNaoIniciaNemAudita() {
        assertThatThrownBy(() -> service.iniciar(7, 8, 1)).isInstanceOf(VersaoEventoDivergenteException.class);
        verifyNoInteractions(jdbc, auditoria, outbox);
    }

    @Test void faltaDePreparoNaoExecutaTransicao() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(false);
        assertThatThrownBy(() -> service.iniciar(7, 8, 2)).isInstanceOf(ConflitoException.class)
                .hasMessageContaining("preço");
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.RASCUNHO);
        verifyNoInteractions(auditoria, outbox);
    }

    @Test void eventoFuturoNaoAbrePortariaAntesDaJanela() {
        ReflectionTestUtils.setField(evento, "inicio", OffsetDateTime.now().plusDays(1));
        assertThatThrownBy(() -> service.iniciar(7, 8, 2)).isInstanceOf(ConflitoException.class)
                .hasMessageContaining("período");
        verifyNoInteractions(auditoria, outbox);
    }

    @Test void resumoSemLeituraNaoConsultaFontesFinanceirasNemFiscais() {
        when(eventos.findByIdAndOrganizacaoId(8L, 7L)).thenReturn(Optional.of(evento));
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(new PatioEventoOperacaoService.Operacao(0, 0, 0, 0, 0));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        var resumo = service.resumo(7, 8, false, false);
        assertThat(resumo.financeiro()).isNull();
        assertThat(resumo.fiscal()).isNull();
        verify(jdbc, never()).query(contains("PENDENTE_EMISSAO"), any(RowMapper.class), any(Object[].class));
        verify(jdbc, never()).queryForObject(contains("WITH recebimentos"), any(RowMapper.class), any(Object[].class));
    }
}
