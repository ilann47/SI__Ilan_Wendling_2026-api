package com.kaneko.estacionamento.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;

class AtribuicaoAcessoTest {

    @Test
    void nasceAtivaERevogacaoEhTerminal() {
        var atribuicao = AtribuicaoAcesso.conceder(10L, 20L, 30L, 30L);
        assertThat(atribuicao.estaVigente()).isTrue();
        atribuicao.revogar("Mudanca de funcao");
        assertThat(atribuicao.estaVigente()).isFalse();
        assertThatThrownBy(() -> atribuicao.revogar("Novamente"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void respeitaVigenciaERejeitaIntervaloIncoerente() {
        OffsetDateTime futuro = OffsetDateTime.now().plusDays(1);
        var futura = AtribuicaoAcesso.conceder(10L, 20L, 30L, 30L, futuro, futuro.plusDays(1));
        assertThat(futura.estaVigente()).isFalse();
        futura.revogar("Escala cancelada antes do inicio");
        assertThat(futura.getRevogadoEm()).isNotNull();
        assertThatThrownBy(() -> AtribuicaoAcesso.conceder(
                10L, 20L, 30L, 30L, futuro, futuro.minusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
