package com.kaneko.estacionamento.credencial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RepresentacaoQrTest {
    @Test
    void criaVersaoComHashEValidadeSemPersistirToken() {
        OffsetDateTime emitidoEm = OffsetDateTime.parse("2026-10-10T16:00:00-03:00");
        OffsetDateTime expiraEm = OffsetDateTime.parse("2026-10-11T01:00:00-03:00");

        RepresentacaoQr representacao = RepresentacaoQr.criar(
                1L, 2L, 1, "a".repeat(64), emitidoEm, expiraEm);

        assertThat(representacao.getOrganizacaoId()).isEqualTo(1L);
        assertThat(representacao.getCredencialId()).isEqualTo(2L);
        assertThat(representacao.getNumeroVersao()).isEqualTo(1);
        assertThat(representacao.getTokenHash()).isEqualTo("a".repeat(64));
        assertThat(representacao.getEmitidoEm()).isEqualTo(emitidoEm);
        assertThat(representacao.getExpiraEm()).isEqualTo(expiraEm);
        assertThat(representacao.getRevogadaEm()).isNull();
    }

    @Test
    void rejeitaHashVersaoOuValidadeInvalidos() {
        OffsetDateTime agora = OffsetDateTime.parse("2026-10-10T16:00:00-03:00");

        assertThatThrownBy(() -> RepresentacaoQr.criar(
                1L, 2L, 0, "token-em-claro", agora, agora.minusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
