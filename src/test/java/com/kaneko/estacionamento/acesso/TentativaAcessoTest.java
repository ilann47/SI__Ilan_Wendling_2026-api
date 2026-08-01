package com.kaneko.estacionamento.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TentativaAcessoTest {
    @Test
    void registraUmaDecisaoTerminalComCodigoEstavel() {
        OffsetDateTime instante = OffsetDateTime.parse("2026-10-10T18:00:00-03:00");
        TentativaAcesso tentativa = TentativaAcesso.receber(
                1L, 2L, 3L, 4L, "PORTAO_NORTE", instante);

        tentativa.avaliar();
        tentativa.autorizar(5L, 6L, 1, instante.plusSeconds(1));

        assertThat(tentativa.getStatus()).isEqualTo(StatusTentativaAcesso.AUTORIZADA);
        assertThat(tentativa.getMotivo()).isEqualTo(MotivoDecisaoAcesso.AUTORIZADA);
        assertThat(tentativa.getCredencialId()).isEqualTo(5L);
        assertThat(tentativa.getOcupacaoResultante()).isEqualTo(1);
        assertThatIllegalStateException().isThrownBy(() -> tentativa.recusar(
                MotivoDecisaoAcesso.CREDENCIAL_INVALIDA, instante.plusSeconds(2)));
    }

    @Test
    void recusaSemExigirCredencialResolvida() {
        OffsetDateTime instante = OffsetDateTime.parse("2026-10-10T18:00:00-03:00");
        TentativaAcesso tentativa = TentativaAcesso.receber(
                1L, 2L, 3L, 4L, "PORTAO_NORTE", instante);

        tentativa.avaliar();
        tentativa.recusar(
                MotivoDecisaoAcesso.CREDENCIAL_INVALIDA, instante.plusSeconds(1));

        assertThat(tentativa.getStatus()).isEqualTo(StatusTentativaAcesso.RECUSADA);
        assertThat(tentativa.getCredencialId()).isNull();
    }
}
