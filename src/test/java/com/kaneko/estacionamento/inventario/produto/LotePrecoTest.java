package com.kaneko.estacionamento.inventario.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class LotePrecoTest {

    @Test
    void criaLoteNormalizandoNomeMoedaEValor() {
        var lote = LotePreco.criar(
                1L, 2L, "  Primeiro Lote  ", new BigDecimal("75.5"), " brl ",
                OffsetDateTime.parse("2026-08-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-09-01T00:00:00-03:00"), 30, 10);

        assertThat(lote.getNome()).isEqualTo("Primeiro Lote");
        assertThat(lote.getPreco()).isEqualByComparingTo("75.50");
        assertThat(lote.getMoeda()).isEqualTo("BRL");
        assertThat(lote.getQuantidade()).isEqualTo(30);
        assertThat(lote.getPrioridade()).isEqualTo(10);
    }

    @Test
    void permiteLoteGratuitoERejeitaDadosInvalidos() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-08-01T00:00:00-03:00");

        assertThat(LotePreco.criar(
                1L, 2L, "Cortesia", BigDecimal.ZERO, "BRL",
                inicio, inicio.plusDays(1), 5, 0).getPreco()).isEqualByComparingTo("0.00");
        assertThatIllegalArgumentException().isThrownBy(() -> LotePreco.criar(
                1L, 2L, "Negativo", new BigDecimal("-0.01"), "BRL",
                inicio, inicio.plusDays(1), 1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> LotePreco.criar(
                1L, 2L, "Escala", new BigDecimal("1.001"), "BRL",
                inicio, inicio.plusDays(1), 1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> LotePreco.criar(
                1L, 2L, "Periodo", BigDecimal.ONE, "BRL",
                inicio, inicio, 1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> LotePreco.criar(
                1L, 2L, "Quantidade", BigDecimal.ONE, "BRL",
                inicio, inicio.plusDays(1), 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> LotePreco.criar(
                1L, 2L, "Prioridade", BigDecimal.ONE, "BRL",
                inicio, inicio.plusDays(1), 1, -1));
    }
}
