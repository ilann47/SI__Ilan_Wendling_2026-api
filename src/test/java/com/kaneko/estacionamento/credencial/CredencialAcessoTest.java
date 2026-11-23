package com.kaneko.estacionamento.credencial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CredencialAcessoTest {
    @Test
    void nascePendenteEAtivaSomenteUmaVezComDireitoCongelado() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");
        OffsetDateTime fim = OffsetDateTime.parse("2026-10-11T01:00:00-03:00");
        CredencialAcesso credencial = CredencialAcesso.criar(
                1L, 2L, 3L, 4L, 5L, 6L, "CRD-0123456789ABCDEF0123456789ABCDEF",
                1, OrigemCredencial.PEDIDO, CategoriaProduto.VIP,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio, fim,
                MeioPreferidoCredencial.QR, "ABC1D23");

        assertThat(credencial.getStatus()).isEqualTo(StatusCredencial.PENDENTE);
        credencial.ativar();

        assertThat(credencial.getStatus()).isEqualTo(StatusCredencial.ATIVA);
        assertThat(credencial.getEventoId()).isEqualTo(2L);
        assertThat(credencial.getPatioId()).isEqualTo(3L);
        assertThat(credencial.getNumeroUnidade()).isEqualTo(1);
        assertThat(credencial.getInicioValidade()).isEqualTo(inicio);
        assertThat(credencial.getFimValidade()).isEqualTo(fim);
        assertThatThrownBy(credencial::ativar)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejeitaEscopoOuCodigoInvalido() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");
        OffsetDateTime fim = OffsetDateTime.parse("2026-10-11T01:00:00-03:00");

        assertThatThrownBy(() -> CredencialAcesso.criar(
                1L, 2L, null, 4L, 5L, 6L, "credencial-fraca", 0,
                OrigemCredencial.PEDIDO, CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio, fim,
                MeioPreferidoCredencial.QR, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registraVersaoQrSomenteParaCredencialQrAtivaENaoExpirada() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");
        OffsetDateTime fim = OffsetDateTime.parse("2026-10-11T01:00:00-03:00");
        CredencialAcesso qr = CredencialAcesso.criar(
                1L, 2L, 3L, 4L, 5L, 6L,
                "CRD-0123456789ABCDEF0123456789ABCDEF", 1,
                OrigemCredencial.PEDIDO, CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio, fim,
                MeioPreferidoCredencial.QR, null);
        CredencialAcesso placa = CredencialAcesso.criar(
                1L, 2L, 3L, 4L, 5L, 6L,
                "CRD-FEDCBA9876543210FEDCBA9876543210", 1,
                OrigemCredencial.PEDIDO, CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio, fim,
                MeioPreferidoCredencial.PLACA, "ABC1D23");

        assertThatThrownBy(() -> qr.registrarRepresentacaoQr(inicio))
                .isInstanceOf(IllegalStateException.class);
        qr.ativar();
        placa.ativar();

        assertThat(qr.registrarRepresentacaoQr(inicio)).isEqualTo(1);
        assertThat(qr.getQrVersao()).isEqualTo(1);
        assertThatThrownBy(() -> qr.registrarRepresentacaoQr(fim))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> placa.registrarRepresentacaoQr(inicio))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bloqueiaCredencialAtivaComMotivoEInstante() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");
        OffsetDateTime fim = OffsetDateTime.parse("2026-10-11T01:00:00-03:00");
        CredencialAcesso credencial = CredencialAcesso.criar(
                1L, 2L, 3L, 4L, 5L, 6L,
                "CRD-0123456789ABCDEF0123456789ABCDEF", 1,
                OrigemCredencial.PEDIDO, CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio, fim,
                MeioPreferidoCredencial.QR, null);
        credencial.ativar();

        assertThat(credencial.bloquear("PEDIDO_CANCELADO", inicio)).isTrue();
        assertThat(credencial.getStatus()).isEqualTo(StatusCredencial.BLOQUEADA);
        assertThat(credencial.getMotivoBloqueio()).isEqualTo("PEDIDO_CANCELADO");
        assertThat(credencial.getBloqueadaEm()).isEqualTo(inicio);
        assertThat(credencial.bloquear("REPLAY", inicio.plusMinutes(1))).isFalse();
    }

    @Test
    void registraEntradaUmaUnicaVezEConsomeOEstadoAtivo() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");
        OffsetDateTime fim = OffsetDateTime.parse("2026-10-11T01:00:00-03:00");
        CredencialAcesso credencial = CredencialAcesso.criar(
                1L, 2L, 3L, 4L, 5L, 6L,
                "CRD-0123456789ABCDEF0123456789ABCDEF", 1,
                OrigemCredencial.PEDIDO, CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio, fim,
                MeioPreferidoCredencial.QR, null);
        credencial.ativar();

        credencial.registrarEntrada();

        assertThat(credencial.getStatus()).isEqualTo(StatusCredencial.DENTRO);
        assertThatThrownBy(credencial::registrarEntrada)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ativa");
    }

    @Test
    void registraSaidaConsumindoOuReativandoConformePoliticaPublicada() {
        CredencialAcesso entradaUnica = novaCredencial(
                "CRD-0123456789ABCDEF0123456789ABCDEF");
        entradaUnica.ativar();
        entradaUnica.registrarEntrada();
        entradaUnica.registrarSaida(false);
        assertThat(entradaUnica.getStatus()).isEqualTo(StatusCredencial.CONSUMIDA);

        CredencialAcesso reentrada = novaCredencial(
                "CRD-FEDCBA9876543210FEDCBA9876543210");
        reentrada.ativar();
        reentrada.registrarEntrada();
        reentrada.registrarSaida(true);
        assertThat(reentrada.getStatus()).isEqualTo(StatusCredencial.ATIVA);
        assertThatThrownBy(() -> entradaUnica.registrarSaida(false))
                .isInstanceOf(IllegalStateException.class);
    }

    private static CredencialAcesso novaCredencial(String codigo) {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-10T17:00:00-03:00");
        return CredencialAcesso.criar(
                1L, 2L, 3L, 4L, 5L, 6L, codigo, 1,
                OrigemCredencial.PEDIDO, CategoriaProduto.COMUM,
                DireitoProduto.ESTACIONAMENTO_EVENTO, inicio,
                inicio.plusHours(8), MeioPreferidoCredencial.QR, null);
    }
}
