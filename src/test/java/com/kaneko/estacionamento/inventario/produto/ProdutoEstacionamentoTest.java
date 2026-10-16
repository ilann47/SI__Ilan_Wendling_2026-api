package com.kaneko.estacionamento.inventario.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProdutoEstacionamentoTest {

    @Test
    void criaOfertaEmRascunhoNormalizandoNomeECodigos() {
        var produto = ProdutoEstacionamento.criar(
                1L, 2L, 3L, "  Estacionamento VIP  ", CategoriaProduto.VIP,
                DireitoProduto.ESTACIONAMENTO_EVENTO,
                OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                30, List.of(" acesso_prioritario "), List.of(" veiculo_leve "));

        assertThat(produto.getNome()).isEqualTo("Estacionamento VIP");
        assertThat(produto.getStatus()).isEqualTo(StatusProdutoEstacionamento.RASCUNHO);
        assertThat(produto.getBeneficios()).containsExactly("ACESSO_PRIORITARIO");
        assertThat(produto.getRestricoes()).containsExactly("VEICULO_LEVE");
    }

    @Test
    void rejeitaQuotaPeriodoECodigoInvalidos() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");

        assertThatIllegalArgumentException().isThrownBy(() -> ProdutoEstacionamento.criar(
                1L, 2L, 3L, "Produto", CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio, inicio.plusHours(1),
                0, List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> ProdutoEstacionamento.criar(
                1L, 2L, 3L, "Produto", CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio, inicio,
                1, List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> ProdutoEstacionamento.criar(
                1L, 2L, 3L, "Produto", CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio, inicio.plusHours(1),
                1, List.of("codigo invalido"), List.of()));
    }

    @Test
    void publicaSomenteUmaVezAPartirDoRascunho() {
        var produto = ProdutoEstacionamento.criar(
                1L, 2L, 3L, "Produto", CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO,
                OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                10, List.of(), List.of());

        produto.publicar();

        assertThat(produto.getStatus()).isEqualTo(StatusProdutoEstacionamento.PUBLICADO);
        assertThatIllegalStateException().isThrownBy(produto::publicar);
    }

    @Test
    void reservaELiberaSaldoSemUltrapassarLimiteComercial() {
        var produto = ProdutoEstacionamento.criar(
                1L, 2L, 3L, "Produto", CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO,
                OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                10, List.of(), List.of());
        produto.publicar();

        produto.reservar(2, 3);
        assertThat(produto.getQuantidadeReservada()).isEqualTo(2);
        assertThatIllegalArgumentException().isThrownBy(() -> produto.reservar(2, 3));

        produto.liberarReserva(2);
        assertThat(produto.getQuantidadeReservada()).isZero();
        assertThatIllegalStateException().isThrownBy(() -> produto.liberarReserva(1));
    }

    @Test
    void converteQuantidadeReservadaEmVendidaSemAlterarComprometido() {
        var produto = ProdutoEstacionamento.criar(
                1L, 2L, 3L, "Produto", CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO,
                OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                10, List.of(), List.of());
        produto.publicar();
        produto.reservar(2, 10);

        produto.confirmarReserva(2);

        assertThat(produto.getQuantidadeReservada()).isZero();
        assertThat(produto.getQuantidadeVendida()).isEqualTo(2);
        assertThatIllegalStateException().isThrownBy(
                () -> produto.confirmarReserva(1));
    }

    @Test
    void cancelamentoCompensaQuantidadeVendidaUmaVez() {
        var produto = ProdutoEstacionamento.criar(
                1L, 2L, 3L, "Produto", CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO,
                OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                10, List.of(), List.of());
        produto.publicar();
        produto.reservar(2, 10);
        produto.confirmarReserva(2);

        produto.cancelarVenda(2);

        assertThat(produto.getQuantidadeVendida()).isZero();
        assertThatIllegalStateException().isThrownBy(
                () -> produto.cancelarVenda(1));
    }
}
