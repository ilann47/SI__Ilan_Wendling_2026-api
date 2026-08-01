package com.kaneko.estacionamento.inventario.alocacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AlocacaoPatioEventoTest {

    @Test
    void criaAlocacaoComPeriodoECapacidadesValidas() {
        var alocacao = AlocacaoPatioEvento.criar(
                1L, 2L, 3L,
                OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                100, 80, 20);

        assertThat(alocacao.getOrganizacaoId()).isEqualTo(1L);
        assertThat(alocacao.getEventoId()).isEqualTo(2L);
        assertThat(alocacao.getPatioId()).isEqualTo(3L);
        assertThat(alocacao.getCapacidadeOperacional()).isEqualTo(100);
    }

    @Test
    void rejeitaPeriodoInvalidoEHierarquiaDeCapacidadeInvalida() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");

        assertThatIllegalArgumentException().isThrownBy(() -> AlocacaoPatioEvento.criar(
                1L, 2L, 3L, inicio, inicio, 100, 80, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> AlocacaoPatioEvento.criar(
                1L, 2L, 3L, inicio, inicio.plusHours(1), 100, 90, 11));
    }

    @Test
    void serializaOcupacaoSemUltrapassarCapacidadeOperacional() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");
        var alocacao = AlocacaoPatioEvento.criar(
                1L, 2L, 3L, inicio, inicio.plusHours(4), 2, 2, 0);

        assertThat(alocacao.registrarEntrada()).isEqualTo(1);
        assertThat(alocacao.registrarEntrada()).isEqualTo(2);
        assertThat(alocacao.getOcupacaoAtual()).isEqualTo(2);
        assertThatIllegalStateException().isThrownBy(alocacao::registrarEntrada)
                .withMessageContaining("lotado");
        assertThatIllegalArgumentException().isThrownBy(
                () -> alocacao.alterarCapacidades(1, 1, 0))
                .withMessageContaining("ocupacao");
    }

    @Test
    void decrementaOcupacaoSemPermitirValorNegativo() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");
        var alocacao = AlocacaoPatioEvento.criar(
                1L, 2L, 3L, inicio, inicio.plusHours(4), 2, 2, 0);
        alocacao.registrarEntrada();

        assertThat(alocacao.registrarSaida()).isZero();
        assertThatIllegalStateException().isThrownBy(alocacao::registrarSaida)
                .withMessageContaining("ocupacao");
    }
}
