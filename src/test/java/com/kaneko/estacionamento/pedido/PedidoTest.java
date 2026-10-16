package com.kaneko.estacionamento.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PedidoTest {

    @Test
    void criaComTotalDerivadoEAvancaParaAguardarPagamentoUmaVez() {
        Pedido pedido = Pedido.criar(
                1L, 2L, 3L, 4L, "PED-0123456789ABCDEF0123456789ABCDEF",
                "DIRECT_WEB", "Comprador",
                "comprador@example.com", "12345678909", "ABC1D23",
                OffsetDateTime.parse("2099-07-30T12:15:00Z"));
        ItemPedido item = ItemPedido.criar(
                pedido, 5L, "Estacionamento VIP", 2,
                new BigDecimal("75.00"), "BRL");
        pedido.adicionarItem(item);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(pedido.getSubtotal()).isEqualByComparingTo("150.00");
        assertThat(pedido.getTotal()).isEqualByComparingTo("150.00");
        assertThat(pedido.getMoeda()).isEqualTo("BRL");

        pedido.aguardarPagamento();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThatIllegalStateException().isThrownBy(pedido::aguardarPagamento);
    }

    @Test
    void expiraSomenteAoAtingirPrazoEnquantoAguardaPagamento() {
        OffsetDateTime expiraEm = OffsetDateTime.parse("2099-07-30T12:15:00Z");
        Pedido pedido = Pedido.criar(
                1L, 2L, 3L, 4L, "PED-FEDCBA9876543210FEDCBA9876543210",
                "DIRECT_WEB", "Comprador", "comprador@example.com", null,
                null, expiraEm);
        pedido.adicionarItem(ItemPedido.criar(
                pedido, 5L, "Estacionamento", 1, new BigDecimal("10"), "BRL"));
        pedido.aguardarPagamento();

        assertThat(pedido.expirar(expiraEm.minusNanos(1))).isFalse();
        assertThat(pedido.expirar(expiraEm)).isTrue();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EXPIRADO);
        assertThat(pedido.getFinalizadoEm()).isEqualTo(expiraEm);
    }

    @Test
    void confirmaSomentePedidoAguardandoPagamento() {
        Pedido pedido = pedidoAguardandoPagamento();

        pedido.confirmar();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CONFIRMADO);
        assertThatIllegalStateException().isThrownBy(pedido::confirmar);
    }

    @Test
    void atendeSomentePedidoConfirmado() {
        Pedido pedido = pedidoAguardandoPagamento();
        pedido.confirmar();

        pedido.atender();

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ATENDIDO);
        assertThatIllegalStateException().isThrownBy(pedido::atender);
    }

    @Test
    void cancelaDiretoAntesDaConfirmacaoEExigeReembolsoDepois() {
        OffsetDateTime agora = OffsetDateTime.parse("2099-07-30T12:00:00Z");
        Pedido aguardando = pedidoAguardandoPagamento();

        assertThat(aguardando.cancelar(agora)).isFalse();
        assertThat(aguardando.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(aguardando.getFinalizadoEm()).isEqualTo(agora);
        assertThatIllegalStateException().isThrownBy(() -> aguardando.cancelar(agora));

        Pedido confirmado = pedidoAguardandoPagamento();
        confirmado.confirmar();
        assertThat(confirmado.cancelar(agora)).isTrue();
        assertThat(confirmado.getStatus()).isEqualTo(StatusPedido.EM_REEMBOLSO);
        assertThat(confirmado.getFinalizadoEm()).isNull();

        Pedido atendido = pedidoAguardandoPagamento();
        atendido.confirmar();
        atendido.atender();
        assertThat(atendido.cancelar(agora)).isTrue();
        assertThat(atendido.getStatus()).isEqualTo(StatusPedido.EM_REEMBOLSO);
    }

    private static Pedido pedidoAguardandoPagamento() {
        Pedido pedido = Pedido.criar(
                1L, 2L, 3L, 4L, "PED-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "DIRECT_WEB", "Comprador", "comprador@example.com", null,
                null, OffsetDateTime.parse("2099-07-30T12:15:00Z"));
        pedido.adicionarItem(ItemPedido.criar(
                pedido, 5L, "Estacionamento", 1, new BigDecimal("10"), "BRL"));
        pedido.aguardarPagamento();
        return pedido;
    }
}
