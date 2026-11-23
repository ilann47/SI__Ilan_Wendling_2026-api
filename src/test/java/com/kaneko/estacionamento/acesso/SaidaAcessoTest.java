package com.kaneko.estacionamento.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SaidaAcessoTest {
    @Test
    void encerraPresencaESessaoUmaUnicaVez() {
        OffsetDateTime entrada = OffsetDateTime.parse("2026-10-10T19:00:00-03:00");
        OffsetDateTime saida = entrada.plusHours(2);
        Presenca presenca = Presenca.entrar(1L, 2L, 3L, 4L, 5L, entrada);
        SessaoEstacionamento sessao = SessaoEstacionamento.abrir(
                1L, 2L, 3L, 4L, 6L, 7L, 8L, 5L, null, entrada);

        presenca.sair(9L, saida);
        sessao.encerrar(9L, saida);

        assertThat(presenca.getStatus()).isEqualTo(StatusPresenca.FORA);
        assertThat(presenca.getTentativaCheckoutId()).isEqualTo(9L);
        assertThat(sessao.getStatus()).isEqualTo(StatusSessaoEstacionamento.ENCERRADA);
        assertThat(sessao.getTentativaSaidaId()).isEqualTo(9L);
        assertThatIllegalStateException().isThrownBy(() -> presenca.sair(10L, saida));
        assertThatIllegalStateException().isThrownBy(() -> sessao.encerrar(10L, saida));
    }

    @Test
    void tentativaDeSaidaPodeAutorizarOcupacaoZero() {
        OffsetDateTime instante = OffsetDateTime.parse("2026-10-10T21:00:00-03:00");
        TentativaAcesso tentativa = TentativaAcesso.receberSaida(
                1L, 2L, 3L, 4L, "PORTAO_SUL", instante);
        tentativa.avaliar();
        tentativa.autorizar(5L, 6L, 0, instante);

        assertThat(tentativa.getDirecao()).isEqualTo("SAIDA");
        assertThat(tentativa.getOcupacaoResultante()).isZero();
    }
}
