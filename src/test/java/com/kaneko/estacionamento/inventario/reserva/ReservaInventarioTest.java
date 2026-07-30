package com.kaneko.estacionamento.inventario.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ReservaInventarioTest {

    @Test
    void nasceMantidaETransicoesTerminaisDevolvemSaldoUmaVez() {
        OffsetDateTime agora = OffsetDateTime.parse("2099-07-30T12:00:00Z");
        ReservaInventario liberada = ReservaInventario.criar(
                1L, 2L, 3L, 4L, 2, "DIRECT_WEB", "cart-1", "key-1",
                9L, new BigDecimal("75.00"), "BRL", agora.plusMinutes(15));

        assertThat(liberada.getStatus()).isEqualTo(StatusReservaInventario.MANTIDA);
        assertThat(liberada.liberar("DESISTENCIA", agora.plusMinutes(1))).isTrue();
        assertThat(liberada.getStatus()).isEqualTo(StatusReservaInventario.LIBERADA);
        assertThat(liberada.liberar("REPLAY", agora.plusMinutes(2))).isFalse();
        assertThatIllegalStateException().isThrownBy(
                () -> liberada.expirar(agora.plusMinutes(20)));

        ReservaInventario expirada = ReservaInventario.criar(
                1L, 2L, 3L, 4L, 1, "DIRECT_WEB", null, "key-2",
                9L, new BigDecimal("75.00"), "BRL", agora.plusMinutes(15));
        assertThat(expirada.expirar(agora.plusMinutes(14))).isFalse();
        assertThat(expirada.expirar(agora.plusMinutes(15))).isTrue();
        assertThat(expirada.getStatus()).isEqualTo(StatusReservaInventario.EXPIRADA);
        assertThat(expirada.expirar(agora.plusMinutes(16))).isFalse();
    }

    @Test
    void validaQuantidadeCanalPrecoEExpiracao() {
        OffsetDateTime agora = OffsetDateTime.parse("2099-07-30T12:00:00Z");
        assertThatIllegalArgumentException().isThrownBy(() -> ReservaInventario.criar(
                1L, 2L, 3L, 4L, 0, "direct-web", null, "key",
                9L, BigDecimal.ONE, "BRL", agora));
    }

    @Test
    void confirmaHoldMantidoAntesDoPrazoUmaUnicaVez() {
        OffsetDateTime agora = OffsetDateTime.parse("2099-07-30T12:00:00Z");
        ReservaInventario reserva = ReservaInventario.criar(
                1L, 2L, 3L, 4L, 1, "DIRECT_WEB", null, "key-confirm",
                9L, BigDecimal.TEN, "BRL", agora.plusMinutes(15));

        assertThat(reserva.confirmar(agora.plusMinutes(1))).isTrue();
        assertThat(reserva.getStatus()).isEqualTo(StatusReservaInventario.CONFIRMADA);
        assertThat(reserva.confirmar(agora.plusMinutes(2))).isFalse();
        assertThatIllegalStateException().isThrownBy(
                () -> reserva.liberar("DESISTENCIA", agora.plusMinutes(3)));
    }
}
