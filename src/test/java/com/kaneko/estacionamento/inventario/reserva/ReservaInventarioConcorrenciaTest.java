package com.kaneko.estacionamento.inventario.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.credencial.CredencialAcessoService;
import com.kaneko.estacionamento.credencial.MeioPreferidoCredencial;
import com.kaneko.estacionamento.credencial.dto.CredencialAcessoResponse;
import com.kaneko.estacionamento.credencial.dto.EmitirCredencialRequest;
import com.kaneko.estacionamento.evento.EventoService;
import com.kaneko.estacionamento.evento.dto.AbrirVendasEventoRequest;
import com.kaneko.estacionamento.evento.dto.CriarEventoRequest;
import com.kaneko.estacionamento.evento.dto.PublicarEventoRequest;
import com.kaneko.estacionamento.geografia.CidadeService;
import com.kaneko.estacionamento.geografia.EstadoService;
import com.kaneko.estacionamento.geografia.PaisService;
import com.kaneko.estacionamento.geografia.dto.CidadeRequest;
import com.kaneko.estacionamento.geografia.dto.EstadoRequest;
import com.kaneko.estacionamento.geografia.dto.PaisRequest;
import com.kaneko.estacionamento.instalacoes.InstalacoesService;
import com.kaneko.estacionamento.instalacoes.dto.CriarPatioInstalacaoRequest;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoService;
import com.kaneko.estacionamento.inventario.alocacao.dto.CriarAlocacaoPatioEventoRequest;
import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import com.kaneko.estacionamento.inventario.produto.LotePrecoService;
import com.kaneko.estacionamento.inventario.produto.ProdutoEstacionamentoService;
import com.kaneko.estacionamento.inventario.produto.dto.CriarLotePrecoRequest;
import com.kaneko.estacionamento.inventario.produto.dto.CriarProdutoEstacionamentoRequest;
import com.kaneko.estacionamento.inventario.produto.dto.PublicarProdutoRequest;
import com.kaneko.estacionamento.inventario.reserva.dto.CriarReservaInventarioRequest;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.venue.VenueService;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import com.kaneko.estacionamento.pedido.PedidoService;
import com.kaneko.estacionamento.pedido.VersaoPedidoDivergenteException;
import com.kaneko.estacionamento.pedido.dto.CompradorPedidoRequest;
import com.kaneko.estacionamento.pedido.dto.ConfirmarPedidoManualmenteRequest;
import com.kaneko.estacionamento.pedido.dto.CriarPedidoRequest;
import com.kaneko.estacionamento.pedido.dto.PedidoResponse;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "app.security.enabled=true")
class ReservaInventarioConcorrenciaTest {
    @Autowired OrganizacaoService organizacoes;
    @Autowired UsuarioService usuarios;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired VenueService venues;
    @Autowired InstalacoesService instalacoes;
    @Autowired EventoService eventos;
    @Autowired AlocacaoPatioEventoService alocacoes;
    @Autowired ProdutoEstacionamentoService produtos;
    @Autowired LotePrecoService lotes;
    @Autowired ReservaInventarioService reservas;
    @Autowired PedidoService pedidos;
    @Autowired CredencialAcessoService credenciais;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AuditoriaService auditoria;
    @MockitoBean OutboxService outbox;

    private Long organizacaoId;
    private Long usuarioId;
    private Long paisId;
    private Long estadoId;
    private Long cidadeId;
    private Long produtoId;

    @BeforeEach
    void prepararUltimaVaga() {
        Usuario usuario = usuarios.criar(new UsuarioRequest(
                "Usuario Concorrencia", "hold_concorrencia", "senha123",
                null, null, PerfilUsuario.ADMIN));
        usuarioId = usuario.getId();
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Concorrencia Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        var pais = paises.criar(new PaisRequest(
                "Brasil Concorrencia", "BR", null, null, null, null, true));
        paisId = pais.getId();
        var estado = estados.criar(new EstadoRequest(
                "Parana Concorrencia", "PR", paisId, true));
        estadoId = estado.getId();
        cidadeId = cidades.criar(new CidadeRequest(
                "Curitiba Concorrencia", null, null, estadoId, true)).getId();
        Long venueId = venues.criar(organizacaoId, new CriarVenueRequest(
                "Arena Concorrencia", "Rua Evento", null, null, "Centro",
                "80000-000", cidadeId, "America/Sao_Paulo")).id();
        Long patioId = instalacoes.criar(organizacaoId, new CriarPatioInstalacaoRequest(
                "Patio Concorrencia", "Rua Patio", null, null, "Centro",
                "80000-000", cidadeId, 1, 1, null, true)).id();
        Long eventoId = eventos.criar(
                organizacaoId, usuarioId, "evento-concorrencia",
                new CriarEventoRequest(
                        "Evento Concorrencia", venueId,
                        OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-10T23:30:00-03:00"),
                        "America/Sao_Paulo", null)).id();
        Long alocacaoId = alocacoes.criar(
                organizacaoId, usuarioId, eventoId, "alocacao-concorrencia",
                new CriarAlocacaoPatioEventoRequest(
                        patioId, OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                        1, 1, 0)).id();
        produtoId = produtos.criar(
                organizacaoId, usuarioId, eventoId, "produto-concorrencia",
                new CriarProdutoEstacionamentoRequest(
                        "Produto Ultima Vaga", CategoriaProduto.COMUM, alocacaoId,
                        OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                        DireitoProduto.ESTACIONAMENTO_EVENTO, 1, List.of(), List.of())).id();
        OffsetDateTime agora = OffsetDateTime.now();
        lotes.criar(
                organizacaoId, usuarioId, produtoId, "lote-concorrencia",
                new CriarLotePrecoRequest(
                        "Lote Ultima Vaga", new BigDecimal("50.00"), "BRL",
                        agora.minusDays(1), agora.plusDays(1), 1, 1));
        produtos.publicar(
                organizacaoId, produtoId, 0L, new PublicarProdutoRequest(true));
        eventos.publicar(
                organizacaoId, eventoId, 0L, new PublicarEventoRequest(true));
        eventos.abrirVendas(
                organizacaoId, eventoId, 2L, new AbrirVendasEventoRequest(null));
    }

    @Test
    void apenasUmaTransacaoConquistaAUltimaVaga() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> primeira = executor.submit(() -> reservar(inicio, "concorrente-a"));
            Future<Object> segunda = executor.submit(() -> reservar(inicio, "concorrente-b"));
            inicio.countDown();

            List<Object> resultados = List.of(primeira.get(), segunda.get());
            assertThat(resultados.stream()
                    .filter(r -> r instanceof com.kaneko.estacionamento.inventario.reserva.dto.ReservaInventarioResponse)
                    .count()).isEqualTo(1);
            assertThat(resultados.stream().filter(r -> r instanceof ConflitoException).count())
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select quantidade_reservada from produto_estacionamento where id = ?",
                    Integer.class, produtoId)).isEqualTo(1);
        }
    }

    @Test
    void apenasUmaConfirmacaoConverteAReservaEmVenda() throws Exception {
        Long reservaId = reservas.criar(
                organizacaoId, usuarioId, "hold-confirm-concorrente",
                new CriarReservaInventarioRequest(
                        produtoId, 1, "DIRECT_WEB", "buyer-concorrente")).id();
        PedidoResponse pedido = pedidos.criar(
                organizacaoId, usuarioId, "order-confirm-concorrente",
                new CriarPedidoRequest(
                        reservaId,
                        new CompradorPedidoRequest(
                                "Comprador Concorrente", "buyer@example.com", null),
                        null, "DIRECT_WEB"));
        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> primeira = executor.submit(
                    () -> confirmar(inicio, pedido.id(), "confirm-a"));
            Future<Object> segunda = executor.submit(
                    () -> confirmar(inicio, pedido.id(), "confirm-b"));
            inicio.countDown();

            List<Object> resultados = List.of(primeira.get(), segunda.get());
            assertThat(resultados.stream().filter(r -> r instanceof PedidoResponse).count())
                    .isEqualTo(1);
            assertThat(resultados.stream()
                    .filter(r -> r instanceof VersaoPedidoDivergenteException).count())
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    select quantidade_reservada from produto_estacionamento where id = ?
                    """, Integer.class, produtoId)).isZero();
            assertThat(jdbc.queryForObject("""
                    select quantidade_vendida from produto_estacionamento where id = ?
                    """, Integer.class, produtoId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    select count(*) from confirmacao_manual_pedido where pedido_id = ?
                    """, Integer.class, pedido.id())).isEqualTo(1);
        }
    }

    @Test
    void apenasUmaEmissaoAtendeAUnicaUnidadeDoPedido() throws Exception {
        Long reservaId = reservas.criar(
                organizacaoId, usuarioId, "hold-credential-concorrente",
                new CriarReservaInventarioRequest(
                        produtoId, 1, "DIRECT_WEB", "buyer-credential")).id();
        PedidoResponse pedido = pedidos.criar(
                organizacaoId, usuarioId, "order-credential-concorrente",
                new CriarPedidoRequest(
                        reservaId,
                        new CompradorPedidoRequest(
                                "Comprador Credencial", "credential@example.com", null),
                        null, "DIRECT_WEB"));
        pedidos.confirmarManualmente(
                organizacaoId, usuarioId, pedido.id(), 1L, "confirm-credential",
                new ConfirmarPedidoManualmenteRequest(
                        "DINHEIRO", "RECIBO-CREDENTIAL",
                        "RECEBIMENTO_PRESENCIAL"));

        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> primeira = executor.submit(
                    () -> emitir(inicio, pedido.id(), "credential-a"));
            Future<Object> segunda = executor.submit(
                    () -> emitir(inicio, pedido.id(), "credential-b"));
            inicio.countDown();

            List<Object> resultados = List.of(primeira.get(), segunda.get());
            assertThat(resultados.stream()
                    .filter(r -> r instanceof CredencialAcessoResponse).count())
                    .isEqualTo(1);
            assertThat(resultados.stream()
                    .filter(r -> r instanceof ConflitoException).count())
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from credencial_acesso where pedido_id = ?",
                    Integer.class, pedido.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select status from pedido where id = ?",
                    String.class, pedido.id())).isEqualTo("ATENDIDO");
        }
    }

    private Object reservar(CountDownLatch inicio, String chave) {
        try {
            inicio.await();
            return reservas.criar(
                    organizacaoId, usuarioId, chave,
                    new CriarReservaInventarioRequest(
                            produtoId, 1, "DIRECT_WEB", chave));
        } catch (Exception ex) {
            return ex;
        }
    }

    private Object confirmar(CountDownLatch inicio, Long pedidoId, String chave) {
        try {
            inicio.await();
            return pedidos.confirmarManualmente(
                    organizacaoId, usuarioId, pedidoId, 1L, chave,
                    new ConfirmarPedidoManualmenteRequest(
                            "DINHEIRO", "RECIBO-" + chave,
                            "RECEBIMENTO_PRESENCIAL"));
        } catch (Exception ex) {
            return ex;
        }
    }

    private Object emitir(CountDownLatch inicio, Long pedidoId, String chave) {
        try {
            inicio.await();
            return credenciais.emitir(
                    organizacaoId, usuarioId, pedidoId, chave,
                    new EmitirCredencialRequest(MeioPreferidoCredencial.QR));
        } catch (Exception ex) {
            return ex;
        }
    }

    @AfterEach
    void limparDadosCommitados() {
        if (organizacaoId == null) return;
        jdbc.update("delete from requisicao_idempotente where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from credencial_acesso where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from confirmacao_manual_pedido where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from item_pedido where pedido_id in (select id from pedido where organizacao_id = ?)", organizacaoId);
        jdbc.update("delete from comprador_pedido where pedido_id in (select id from pedido where organizacao_id = ?)", organizacaoId);
        jdbc.update("delete from pedido where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from reserva_inventario where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from lote_preco where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from produto_estacionamento where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from alocacao_patio_evento where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from evento where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from unidade_operacional where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from patio where organizacao_id = ?", organizacaoId);
        jdbc.update("delete from organizacao where id = ?", organizacaoId);
        if (usuarioId != null) jdbc.update("delete from usuario where id = ?", usuarioId);
        if (cidadeId != null) jdbc.update("delete from cidade where id = ?", cidadeId);
        if (estadoId != null) jdbc.update("delete from estado where id = ?", estadoId);
        if (paisId != null) jdbc.update("delete from pais where id = ?", paisId);
    }
}
