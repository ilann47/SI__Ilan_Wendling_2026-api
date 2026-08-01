package com.kaneko.estacionamento.inventario.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.auditoria.RegistroAuditoriaRepository;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.common.outbox.EventoOutboxRepository;
import com.kaneko.estacionamento.evento.EventoService;
import com.kaneko.estacionamento.evento.dto.AbrirVendasEventoRequest;
import com.kaneko.estacionamento.evento.dto.AtualizarEventoRequest;
import com.kaneko.estacionamento.evento.PoliticaReentrada;
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
import com.kaneko.estacionamento.inventario.reserva.ReservaInventarioService;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.venue.VenueService;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import com.kaneko.estacionamento.pedido.PedidoService;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Transactional
class ProdutoEstacionamentoControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired VenueService venues;
    @Autowired InstalacoesService instalacoes;
    @Autowired EventoService eventos;
    @Autowired AlocacaoPatioEventoService alocacoes;
    @Autowired JwtService jwt;
    @Autowired RegistroAuditoriaRepository auditorias;
    @Autowired EventoOutboxRepository outbox;
    @Autowired ReservaInventarioService reservas;
    @Autowired PedidoService pedidos;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private Usuario admin;
    private Long organizacaoId;
    private Long venueId;
    private Long patioId;
    private Long eventoId;
    private Long alocacaoId;
    private String tokenGlobal;
    private String tokenContextual;

    @BeforeEach
    void setup() throws Exception {
        admin = usuario("produto_admin", PerfilUsuario.ADMIN);
        tokenGlobal = bearer(jwt.gerarToken(admin.getLogin(), "ADMIN"));
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Produtos Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        criarMembership(admin.getId(), tokenGlobal);
        tokenContextual = selecionar(organizacaoId, tokenGlobal);
        Long cidadeId = criarCidade("Curitiba Produtos");
        venueId = venues.criar(organizacaoId, new CriarVenueRequest(
                "Arena Produtos", "Avenida Evento", "500", null, "Centro",
                "80000-000", cidadeId, "America/Sao_Paulo")).id();
        patioId = instalacoes.criar(organizacaoId, new CriarPatioInstalacaoRequest(
                "Patio Produtos", "Rua Patio", null, null, "Centro", "80000-000",
                cidadeId, 100, 100, null, true)).id();
        eventoId = criarEvento("Evento Produtos", "evento-produtos-base");
        alocacaoId = criarAlocacao(eventoId, patioId, 100, 80, 20, "alloc-produtos-base");
    }

    @Test
    void criaProdutoRascunhoComAuditoria() throws Exception {
        String body = criarProduto(tokenContextual, eventoId, "produto-1",
                        payload("Estacionamento VIP", alocacaoId, "VIP", 30,
                                "2026-10-10T17:30:00-03:00", "2026-10-11T00:30:00-03:00"),
                        status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.eventId").value(eventoId))
                .andExpect(jsonPath("$.parkingAllocationId").value(alocacaoId))
                .andExpect(jsonPath("$.name").value("Estacionamento VIP"))
                .andExpect(jsonPath("$.category").value("VIP"))
                .andExpect(jsonPath("$.right").value("ESTACIONAMENTO_EVENTO"))
                .andExpect(jsonPath("$.quota").value(30))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.benefits[0]").value("ACESSO_PRIORITARIO"))
                .andExpect(jsonPath("$.restrictions[0]").value("VEICULO_LEVE"))
                .andReturn().getResponse().getContentAsString();
        long produtoId = JsonPath.<Number>read(body, "$.id").longValue();

        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao()).isEqualTo("PRODUTO_ESTACIONAMENTO_CRIADO");
            assertThat(auditoria.getRecursoId()).isEqualTo(produtoId);
            assertThat(auditoria.getEventoId()).isEqualTo(eventoId);
            assertThat(auditoria.getPatioId()).isEqualTo(patioId);
        });
    }

    @Test
    void criacaoEhIdempotenteEChaveNaoAceitaOutroPayload() throws Exception {
        String payload = payload("Produto Comum", alocacaoId, "COMUM", 20,
                "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00");
        String primeiro = criarProduto(tokenContextual, eventoId, "produto-idempotente",
                payload, status().isCreated()).andReturn().getResponse().getContentAsString();
        String repetido = criarProduto(tokenContextual, eventoId, "produto-idempotente",
                payload, status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Number>read(repetido, "$.id").longValue())
                .isEqualTo(JsonPath.<Number>read(primeiro, "$.id").longValue());
        criarProduto(tokenContextual, eventoId, "produto-idempotente",
                payload("Produto Alterado", alocacaoId, "COMUM", 20,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isConflict());
    }

    @Test
    void impedeQueSomaDasQuotasExcedaCapacidadeVendavel() throws Exception {
        criarProduto(tokenContextual, eventoId, "produto-quota-60",
                payload("Produto Sessenta", alocacaoId, "COMUM", 60,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isCreated());
        criarProduto(tokenContextual, eventoId, "produto-quota-20",
                payload("Produto Vinte", alocacaoId, "VIP", 20,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isCreated());
        criarProduto(tokenContextual, eventoId, "produto-quota-excedente",
                payload("Produto Excedente", alocacaoId, "PCD", 1,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isConflict());
    }

    @Test
    void exigeJanelaContidaNaAlocacaoEVinculoComEvento() throws Exception {
        criarProduto(tokenContextual, eventoId, "produto-janela-invalida",
                payload("Janela Invalida", alocacaoId, "COMUM", 10,
                        "2026-10-10T16:59:59-03:00", "2026-10-11T01:00:00-03:00"),
                status().isBadRequest());

        Long outroEvento = criarEvento("Outro Evento Produtos", "evento-produtos-outro");
        criarProduto(tokenContextual, outroEvento, "produto-alocacao-outro-evento",
                payload("Produto Cruzado", alocacaoId, "COMUM", 10,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isNotFound());
    }

    @Test
    void permiteGestorEOrganizadorMasNegaMembroSemPapel() throws Exception {
        Usuario gestor = usuario("produto_gestor", PerfilUsuario.OPERADOR);
        long gestorMembership = criarMembership(gestor.getId(), tokenContextual);
        concederPapel(gestorMembership, "GESTOR");
        String tokenGestor = selecionar(organizacaoId,
                bearer(jwt.gerarToken(gestor.getLogin(), "OPERADOR")));
        criarProduto(tokenGestor, eventoId, "produto-gestor",
                payload("Produto Gestor", alocacaoId, "STAFF", 5,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isCreated());

        Usuario organizador = usuario("produto_organizador", PerfilUsuario.OPERADOR);
        long organizadorMembership = criarMembership(organizador.getId(), tokenContextual);
        concederPapel(organizadorMembership, "ORGANIZADOR");
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));
        criarProduto(tokenOrganizador, eventoId, "produto-organizador",
                payload("Produto Organizador", alocacaoId, "CORTESIA", 5,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isCreated());

        Usuario membro = usuario("produto_membro", PerfilUsuario.OPERADOR);
        criarMembership(membro.getId(), tokenContextual);
        String tokenMembro = selecionar(organizacaoId,
                bearer(jwt.gerarToken(membro.getLogin(), "OPERADOR")));
        criarProduto(tokenMembro, eventoId, "produto-sem-papel",
                payload("Produto Negado", alocacaoId, "COMUM", 5,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isForbidden());
    }

    @Test
    void rejeitaAlocacaoDeOutroTenantECamposControlados() throws Exception {
        Long outraOrganizacao = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Tenant Produtos Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        Long cidadeExterna = criarCidade("Sao Paulo Produtos");
        Long venueExterno = venues.criar(outraOrganizacao, new CriarVenueRequest(
                "Arena Externa", "Rua Externa", null, null, "Centro", "01000-000",
                cidadeExterna, "America/Sao_Paulo")).id();
        Long patioExterno = instalacoes.criar(outraOrganizacao, new CriarPatioInstalacaoRequest(
                "Patio Externo Produtos", "Rua Externa", null, null, "Centro", "01000-000",
                cidadeExterna, 50, 50, null, true)).id();
        Long eventoExterno = eventos.criar(outraOrganizacao, admin.getId(), "evento-externo-produto",
                new CriarEventoRequest("Evento Externo", venueExterno,
                        OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-10T23:30:00-03:00"),
                        "America/Sao_Paulo", null)).id();
        Long alocacaoExterna = criarAlocacao(
                outraOrganizacao, eventoExterno, patioExterno, 50, 50, 0, "alloc-externa-produto");

        criarProduto(tokenContextual, eventoId, "produto-tenant-externo",
                payload("Produto Externo", alocacaoExterna, "COMUM", 10,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isNotFound());
        criarProduto(tokenContextual, eventoId, "produto-campo-controlado",
                payload("Produto Campo", alocacaoId, "COMUM", 10,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00")
                        .replace("{", "{\"organizationId\":999,"),
                status().isBadRequest());
    }

    @Test
    void criaLoteDePrecoGratuitoComAuditoria() throws Exception {
        Long produtoId = criarProdutoBase("Produto com Lote", "produto-com-lote", 30);

        String body = criarLote(tokenContextual, produtoId, "lote-1",
                        payloadLote("Primeiro Lote", "75.50", "BRL", 30, 10,
                                "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                        status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.parkingProductId").value(produtoId))
                .andExpect(jsonPath("$.name").value("Primeiro Lote"))
                .andExpect(jsonPath("$.price").value(75.50))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.quantity").value(30))
                .andExpect(jsonPath("$.priority").value(10))
                .andReturn().getResponse().getContentAsString();
        long loteId = JsonPath.<Number>read(body, "$.id").longValue();

        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao()).isEqualTo("LOTE_PRECO_CRIADO");
            assertThat(auditoria.getRecursoId()).isEqualTo(loteId);
            assertThat(auditoria.getEventoId()).isEqualTo(eventoId);
            assertThat(auditoria.getPatioId()).isEqualTo(patioId);
        });

        criarLote(tokenContextual, produtoId, "lote-gratuito",
                payloadLote("Cortesia", "0", "BRL", 5, 20,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isCreated());
    }

    @Test
    void criacaoDeLoteEhIdempotenteERejeitaChaveComOutroPayload() throws Exception {
        Long produtoId = criarProdutoBase("Produto Idempotente Lote", "produto-idem-lote", 20);
        String payload = payloadLote("Lote Idempotente", "50", "BRL", 20, 1,
                "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00");

        String primeiro = criarLote(tokenContextual, produtoId, "lote-idem", payload,
                status().isCreated()).andReturn().getResponse().getContentAsString();
        String repetido = criarLote(tokenContextual, produtoId, "lote-idem", payload,
                status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(repetido, "$.id").longValue())
                .isEqualTo(JsonPath.<Number>read(primeiro, "$.id").longValue());

        criarLote(tokenContextual, produtoId, "lote-idem",
                payloadLote("Outro Lote", "50", "BRL", 20, 1,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isConflict());
    }

    @Test
    void impedeSobreposicaoAmbiguaMasPermitePrioridadeDeterministica() throws Exception {
        Long produtoId = criarProdutoBase("Produto Prioridades", "produto-prioridades", 40);
        criarLote(tokenContextual, produtoId, "lote-prioridade-base",
                payloadLote("Lote Base", "40", "BRL", 40, 10,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isCreated());
        criarLote(tokenContextual, produtoId, "lote-prioridade-ambigua",
                payloadLote("Lote Ambiguo", "45", "BRL", 40, 10,
                        "2026-08-15T00:00:00-03:00", "2026-09-15T00:00:00-03:00"),
                status().isConflict());
        criarLote(tokenContextual, produtoId, "lote-prioridade-deterministica",
                payloadLote("Lote Prioritario", "45", "BRL", 40, 20,
                        "2026-08-15T00:00:00-03:00", "2026-09-15T00:00:00-03:00"),
                status().isCreated());
    }

    @Test
    void validaQuotaMoedaValorECamposControladosDoLote() throws Exception {
        Long produtoId = criarProdutoBase("Produto Validacoes Lote", "produto-validacoes-lote", 30);

        criarLote(tokenContextual, produtoId, "lote-quota-excedida",
                payloadLote("Quota Excedida", "10", "BRL", 31, 1,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isConflict());
        criarLote(tokenContextual, produtoId, "lote-moeda-divergente",
                payloadLote("Moeda Divergente", "10", "USD", 30, 1,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isBadRequest());
        criarLote(tokenContextual, produtoId, "lote-valor-negativo",
                payloadLote("Valor Negativo", "-0.01", "BRL", 30, 1,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isBadRequest());
        criarLote(tokenContextual, produtoId, "lote-campo-controlado",
                payloadLote("Campo Controlado", "10", "BRL", 30, 1,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00")
                        .replace("{", "{\"organizationId\":999,"),
                status().isBadRequest());
    }

    @Test
    void loteExigePricingManageETenantDoProduto() throws Exception {
        Long produtoId = criarProdutoBase("Produto Protegido Lote", "produto-protegido-lote", 10);
        Usuario membro = usuario("lote_membro", PerfilUsuario.OPERADOR);
        criarMembership(membro.getId(), tokenContextual);
        String tokenMembro = selecionar(organizacaoId,
                bearer(jwt.gerarToken(membro.getLogin(), "OPERADOR")));

        criarLote(tokenMembro, produtoId, "lote-sem-permissao",
                payloadLote("Sem Permissao", "10", "BRL", 10, 1,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isForbidden());

        Long outraOrganizacao = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Tenant Lote Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        criarMembership(outraOrganizacao, admin.getId(), tokenGlobal);
        String tokenOutroTenant = selecionar(outraOrganizacao, tokenGlobal);
        criarLote(tokenOutroTenant, produtoId, "lote-outro-tenant",
                payloadLote("Outro Tenant", "10", "BRL", 10, 1,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isNotFound());
    }

    @Test
    void publicaProdutoCompletoComAuditoriaEOutbox() throws Exception {
        Long produtoId = criarProdutoBase("Produto Publicavel", "produto-publicavel", 20);
        criarLote(tokenContextual, produtoId, "lote-publicavel",
                payloadLote("Lote Publicavel", "50", "BRL", 20, 1,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isCreated());

        publicarProduto(tokenContextual, produtoId, "\"0\"", status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao()).isEqualTo("PRODUTO_ESTACIONAMENTO_PUBLICADO");
            assertThat(auditoria.getRecursoId()).isEqualTo(produtoId);
        });
        assertThat(outbox.findAll()).anySatisfy(evento -> {
            assertThat(evento.getEventType()).isEqualTo("ProdutoEstacionamentoPublicado");
            assertThat(evento.getAggregateId()).isEqualTo(produtoId);
            assertThat(evento.getAggregateVersion()).isEqualTo(1L);
        });
    }

    @Test
    void impedePublicacaoSemLoteEProtegeVersao() throws Exception {
        Long semLote = criarProdutoBase("Produto Sem Lote", "produto-sem-lote", 10);
        publicarProduto(tokenContextual, semLote, "\"0\"", status().isConflict());

        Long produtoId = criarProdutoBase("Produto Versao", "produto-versao", 10);
        criarLote(tokenContextual, produtoId, "lote-versao",
                payloadLote("Lote Versao", "10", "BRL", 10, 1,
                        "2026-08-01T00:00:00-03:00", "2026-09-01T00:00:00-03:00"),
                status().isCreated());
        mvc.perform(post("/api/v1/parking-products/{id}/publication", produtoId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isPreconditionRequired());
        publicarProduto(tokenContextual, produtoId, "\"99\"",
                status().isPreconditionFailed());
        publicarProduto(tokenContextual, produtoId, "\"0\"", status().isOk());
        publicarProduto(tokenContextual, produtoId, "\"0\"",
                status().isPreconditionFailed());
    }

    @Test
    void consultaDisponibilidadeSelecionandoLoteAtivoDeMaiorPrioridade() throws Exception {
        Long produtoComum = criarProdutoBase("Produto Comum Disponivel", "disp-comum", 20);
        Long produtoVip = criarProdutoBaseCategoria(
                "Produto VIP Disponivel", "disp-vip", 20, "VIP");
        OffsetDateTime agora = OffsetDateTime.now().withNano(0);
        String inicio = agora.minusDays(1).toString();
        String fim = agora.plusDays(1).toString();

        criarLote(tokenContextual, produtoComum, "disp-comum-lote",
                payloadLote("Comum Vigente", "30", "BRL", 20, 1, inicio, fim),
                status().isCreated());
        criarLote(tokenContextual, produtoVip, "disp-vip-base",
                payloadLote("VIP Base", "50", "BRL", 20, 1, inicio, fim),
                status().isCreated());
        criarLote(tokenContextual, produtoVip, "disp-vip-prioritario",
                payloadLote("VIP Prioritario", "75", "BRL", 10, 5, inicio, fim),
                status().isCreated());
        publicarProduto(tokenContextual, produtoComum, "\"0\"", status().isOk());
        publicarProduto(tokenContextual, produtoVip, "\"0\"", status().isOk());
        eventos.atualizar(organizacaoId, eventoId, 0L,
                new AtualizarEventoRequest(PoliticaReentrada.ENTRADA_UNICA));
        eventos.publicar(
                organizacaoId, eventoId, 1L, new PublicarEventoRequest(true));

        mvc.perform(get("/api/v1/events/{id}/availability", eventoId)
                        .header("Authorization", tokenContextual)
                        .queryParam("category", "VIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventoId))
                .andExpect(jsonPath("$.guaranteesHold").value(false))
                .andExpect(jsonPath("$.totalAvailable").value(10))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(produtoVip))
                .andExpect(jsonPath("$.items[0].category").value("VIP"))
                .andExpect(jsonPath("$.items[0].availableQuantity").value(10))
                .andExpect(jsonPath("$.items[0].priceTierName").value("VIP Prioritario"))
                .andExpect(jsonPath("$.items[0].price").value(75.00))
                .andExpect(jsonPath("$.items[0].currency").value("BRL"));
    }

    @Test
    void disponibilidadeExigeEventoPublicadoETenantAtivo() throws Exception {
        mvc.perform(get("/api/v1/events/{id}/availability", eventoId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/events/{id}/availability", eventoId)
                        .header("Authorization", tokenGlobal))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/events/{id}/availability", Long.MAX_VALUE)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isNotFound());
    }

    @Test
    void criaHoldComSnapshotIdempotenciaAuditoriaOutboxESaldoProjetado() throws Exception {
        Long produtoId = prepararVendaAberta(5, 3);
        String payload = payloadHold(produtoId, 2, "cart-1");

        String primeiro = criarHold(tokenContextual, "hold-1", payload, status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.parkingProductId").value(produtoId))
                .andExpect(jsonPath("$.eventId").value(eventoId))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.channel").value("DIRECT_WEB"))
                .andExpect(jsonPath("$.buyerReference").value("cart-1"))
                .andExpect(jsonPath("$.unitPrice").value(45.00))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.status").value("MANTIDA"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(primeiro, "$.id").longValue();

        String repetido = criarHold(tokenContextual, "hold-1", payload, status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(repetido, "$.id").longValue()).isEqualTo(holdId);
        criarHold(tokenContextual, "hold-1", payloadHold(produtoId, 1, "cart-1"),
                status().isConflict());

        mvc.perform(get("/api/v1/inventory-holds/{id}", holdId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MANTIDA"));
        mvc.perform(get("/api/v1/events/{id}/availability", eventoId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAvailable").value(1));
        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao()).isEqualTo("RESERVA_INVENTARIO_CRIADA");
            assertThat(auditoria.getRecursoId()).isEqualTo(holdId);
        });
        assertThat(outbox.findAll()).anySatisfy(evento -> {
            assertThat(evento.getEventType()).isEqualTo("InventarioReservado");
            assertThat(evento.getAggregateId()).isEqualTo(holdId);
        });
    }

    @Test
    void liberaHoldExatamenteUmaVezERestauraDisponibilidade() throws Exception {
        Long produtoId = prepararVendaAberta(2, 2);
        String body = criarHold(tokenContextual, "hold-release",
                        payloadHold(produtoId, 2, null), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(body, "$.id").longValue();

        liberarHold(tokenContextual, holdId, "DESISTENCIA", status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.status").value("LIBERADA"))
                .andExpect(jsonPath("$.finalizationReason").value("DESISTENCIA"));
        liberarHold(tokenContextual, holdId, "REPLAY", status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
        mvc.perform(get("/api/v1/events/{id}/availability", eventoId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAvailable").value(2));
        assertThat(outbox.findAll().stream()
                .filter(e -> e.getAggregateId().equals(holdId))
                .filter(e -> e.getEventType().equals("InventarioLiberado"))
                .count()).isEqualTo(1);
    }

    @Test
    void expiraHoldVencidoDevolvendoSaldoEPublicandoDoisFatos() throws Exception {
        Long produtoId = prepararVendaAberta(1, 1);
        String body = criarHold(tokenContextual, "hold-expire",
                        payloadHold(produtoId, 1, null), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(body, "$.id").longValue();
        jdbc.update("""
                update reserva_inventario
                set created_at = now() - interval '1 hour',
                    expira_em = now() - interval '1 minute'
                where id = ?
                """, holdId);
        entityManager.clear();

        assertThat(reservas.expirarVencidas(100)).isEqualTo(1);
        mvc.perform(get("/api/v1/inventory-holds/{id}", holdId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRADA"));
        mvc.perform(get("/api/v1/events/{id}/availability", eventoId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAvailable").value(1));
        assertThat(outbox.findAll().stream()
                .filter(e -> e.getAggregateId().equals(holdId))
                .map(e -> e.getEventType()).toList())
                .contains("ReservaExpirada", "InventarioLiberado");
    }

    @Test
    void holdExigeVendasAbertasProdutoVigenteQuantidadeEContratoValidos() throws Exception {
        Long produtoId = prepararProdutoPublicado(2, 2, false);
        criarHold(tokenContextual, "hold-closed", payloadHold(produtoId, 1, null),
                status().isConflict());
        eventos.abrirVendas(
                organizacaoId, eventoId, 3L, new AbrirVendasEventoRequest(null));
        criarHold(tokenContextual, "hold-excess", payloadHold(produtoId, 3, null),
                status().isConflict());
        criarHold(tokenContextual, "hold-field",
                payloadHold(produtoId, 1, null).replace("{", "{\"status\":\"MANTIDA\","),
                status().isBadRequest());
    }

    @Test
    void holdRespeitaPapelEPropriedadeDoAtor() throws Exception {
        Long produtoId = prepararVendaAberta(3, 3);
        String body = criarHold(tokenContextual, "hold-owner",
                        payloadHold(produtoId, 1, null), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(body, "$.id").longValue();

        Usuario organizador = usuario("hold_organizador", PerfilUsuario.OPERADOR);
        long organizadorMembership = criarMembership(organizador.getId(), tokenContextual);
        concederPapel(organizadorMembership, "ORGANIZADOR");
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));
        mvc.perform(get("/api/v1/inventory-holds/{id}", holdId)
                        .header("Authorization", tokenOrganizador))
                .andExpect(status().isNotFound());
        criarHold(tokenOrganizador, "hold-organizador", payloadHold(produtoId, 1, null),
                status().isCreated());

        Usuario membro = usuario("hold_membro", PerfilUsuario.OPERADOR);
        criarMembership(membro.getId(), tokenContextual);
        String tokenMembro = selecionar(organizacaoId,
                bearer(jwt.gerarToken(membro.getLogin(), "OPERADOR")));
        criarHold(tokenMembro, "hold-negado", payloadHold(produtoId, 1, null),
                status().isForbidden());
    }

    @Test
    void criaPedidoComSnapshotTotalIdempotenciaAuditoriaEOutbox() throws Exception {
        Long produtoId = prepararVendaAberta(4, 4);
        String holdBody = criarHold(tokenContextual, "hold-order-1",
                        payloadHold(produtoId, 2, "checkout-1"), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(holdBody, "$.id").longValue();
        String payload = payloadPedido(holdId, "DIRECT_WEB");

        String primeiro = criarPedido(tokenContextual, "order-1", payload,
                        status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.inventoryHoldId").value(holdId))
                .andExpect(jsonPath("$.eventId").value(eventoId))
                .andExpect(jsonPath("$.number").value(
                        org.hamcrest.Matchers.matchesPattern("PED-[A-F0-9]{32}")))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.vehiclePlate").value("ABC1D23"))
                .andExpect(jsonPath("$.buyer.email").value("comprador@example.com"))
                .andExpect(jsonPath("$.buyer.maskedDocument").value("*******8909"))
                .andExpect(jsonPath("$.items[0].parkingProductId").value(produtoId))
                .andExpect(jsonPath("$.items[0].parkingAllocationId").value(alocacaoId))
                .andExpect(jsonPath("$.items[0].parkingFacilityId").value(patioId))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(45.00))
                .andExpect(jsonPath("$.subtotal").value(90.00))
                .andExpect(jsonPath("$.total").value(90.00))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andReturn().getResponse().getContentAsString();
        long pedidoId = JsonPath.<Number>read(primeiro, "$.id").longValue();

        String repetido = criarPedido(tokenContextual, "order-1", payload,
                        status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(repetido, "$.id").longValue())
                .isEqualTo(pedidoId);
        criarPedido(tokenContextual, "order-1",
                payload.replace("Comprador Kaneko", "Outro Comprador"),
                status().isConflict());
        mvc.perform(get("/api/v1/orders/{id}", pedidoId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pedidoId));

        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao()).isEqualTo("PEDIDO_CRIADO");
            assertThat(auditoria.getRecursoId()).isEqualTo(pedidoId);
            assertThat(auditoria.getEventoId()).isEqualTo(eventoId);
        });
        assertThat(outbox.findAll().stream()
                .filter(e -> e.getAggregateId().equals(pedidoId))
                .map(e -> e.getEventType()).toList())
                .containsExactlyInAnyOrder("PedidoCriado", "PedidoAguardandoPagamento");
    }

    @Test
    void listaPedidosPropriosComCursorEOrdenacaoEstavel() throws Exception {
        Long produtoId = prepararVendaAberta(3, 3);
        long primeiroId = criarPedidoDoProduto(produtoId, "list-1");
        long segundoId = criarPedidoDoProduto(produtoId, "list-2");
        long terceiroId = criarPedidoDoProduto(produtoId, "list-3");

        String primeiraPagina = mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenContextual)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(terceiroId))
                .andExpect(jsonPath("$.items[1].id").value(segundoId))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value(segundoId))
                .andReturn().getResponse().getContentAsString();
        long cursor = JsonPath.<Number>read(
                primeiraPagina, "$.nextCursor").longValue();

        mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenContextual)
                        .param("limit", "2")
                        .param("cursor", Long.toString(cursor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(primeiroId))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void listaPedidosAplicaFiltrosEValidaPaginacao() throws Exception {
        Long produtoId = prepararVendaAberta(2, 2);
        long pedidoId = criarPedidoDoProduto(produtoId, "filter-1");
        criarPedidoDoProduto(produtoId, "filter-2");

        mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenContextual)
                        .param("eventId", Long.toString(eventoId))
                        .param("status", "AGUARDANDO_PAGAMENTO")
                        .param("channel", "direct_web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].id").value(pedidoId));
        mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenContextual)
                        .param("eventId", Long.toString(eventoId + 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenContextual)
                        .param("status", "CONFIRMADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenContextual)
                        .param("cursor", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenContextual)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenContextual)
                        .param("channel", "canal invalido"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pedidoRejeitaHoldLiberadoCanalDivergenteECampoControlado() throws Exception {
        Long produtoId = prepararVendaAberta(3, 3);
        String holdBody = criarHold(tokenContextual, "hold-order-invalid",
                        payloadHold(produtoId, 1, null), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(holdBody, "$.id").longValue();

        criarPedido(tokenContextual, "order-channel",
                payloadPedido(holdId, "BOX_OFFICE"), status().isConflict());
        criarPedido(tokenContextual, "order-field",
                payloadPedido(holdId, "DIRECT_WEB")
                        .replaceFirst("\\{", "{\"status\":\"CONFIRMADO\","),
                status().isBadRequest());
        liberarHold(tokenContextual, holdId, "DESISTENCIA", status().isOk());
        criarPedido(tokenContextual, "order-released",
                payloadPedido(holdId, "DIRECT_WEB"), status().isConflict());
    }

    @Test
    void pedidoRespeitaPapelEPropriedadeDoAtor() throws Exception {
        Long produtoId = prepararVendaAberta(3, 3);
        String holdBody = criarHold(tokenContextual, "hold-order-owner",
                        payloadHold(produtoId, 1, null), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(holdBody, "$.id").longValue();
        String orderBody = criarPedido(tokenContextual, "order-owner",
                        payloadPedido(holdId, "DIRECT_WEB"), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pedidoId = JsonPath.<Number>read(orderBody, "$.id").longValue();

        Usuario organizador = usuario("order_organizador", PerfilUsuario.OPERADOR);
        long organizadorMembership = criarMembership(
                organizador.getId(), tokenContextual);
        concederPapel(organizadorMembership, "ORGANIZADOR");
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));
        mvc.perform(get("/api/v1/orders/{id}", pedidoId)
                        .header("Authorization", tokenOrganizador))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenOrganizador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        Usuario membro = usuario("order_membro", PerfilUsuario.OPERADOR);
        criarMembership(membro.getId(), tokenContextual);
        String tokenMembro = selecionar(organizacaoId,
                bearer(jwt.gerarToken(membro.getLogin(), "OPERADOR")));
        criarPedido(tokenMembro, "order-denied",
                payloadPedido(holdId, "DIRECT_WEB"), status().isForbidden());
        mvc.perform(get("/api/v1/orders")
                        .header("Authorization", tokenMembro))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiraPedidoAguardandoPagamentoComAuditoriaEOutbox() throws Exception {
        Long produtoId = prepararVendaAberta(1, 1);
        String holdBody = criarHold(tokenContextual, "hold-order-expire",
                        payloadHold(produtoId, 1, null), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(holdBody, "$.id").longValue();
        String orderBody = criarPedido(tokenContextual, "order-expire",
                        payloadPedido(holdId, "DIRECT_WEB"), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pedidoId = JsonPath.<Number>read(orderBody, "$.id").longValue();
        jdbc.update("""
                update pedido
                set created_at = now() - interval '1 hour',
                    expira_em = now() - interval '1 minute'
                where id = ?
                """, pedidoId);
        entityManager.clear();

        assertThat(pedidos.expirarVencidos(100)).isEqualTo(1);
        mvc.perform(get("/api/v1/orders/{id}", pedidoId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRADO"))
                .andExpect(jsonPath("$.finalizedAt").exists());
        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao()).isEqualTo("PEDIDO_EXPIRADO");
            assertThat(auditoria.getRecursoId()).isEqualTo(pedidoId);
        });
        assertThat(outbox.findAll()).anySatisfy(evento -> {
            assertThat(evento.getEventType()).isEqualTo("PedidoExpirado");
            assertThat(evento.getAggregateId()).isEqualTo(pedidoId);
        });
    }

    @Test
    void bancoImpedeSegundoPedidoParaOMesmoHold() throws Exception {
        long pedidoId = criarPedidoPersistido("constraint-hold");

        assertThatThrownBy(() -> jdbc.update("""
                insert into pedido (
                    organizacao_id, evento_id, reserva_inventario_id, ator_id,
                    numero, canal, placa_veiculo, status, subtotal, desconto,
                    taxa, total, moeda, expira_em, finalizado_em, version)
                select organizacao_id, evento_id, reserva_inventario_id, ator_id,
                    'PED-FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF', canal, placa_veiculo,
                    status, subtotal, desconto, taxa, total, moeda, expira_em,
                    finalizado_em, 0
                from pedido where id = ?
                """, pedidoId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoImpedeTotalDivergenteDosComponentes() throws Exception {
        long pedidoId = criarPedidoPersistido("constraint-total");

        assertThatThrownBy(() -> jdbc.update("""
                update pedido set total = total + 1 where id = ?
                """, pedidoId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void confirmaPedidoManualmenteConvertendoReservaEmVendaUmaVez() throws Exception {
        long pedidoId = criarPedidoPersistido("manual-confirm");
        Long holdId = jdbc.queryForObject(
                "select reserva_inventario_id from pedido where id = ?",
                Long.class, pedidoId);
        Long produtoId = jdbc.queryForObject(
                "select produto_id from reserva_inventario where id = ?",
                Long.class, holdId);
        String payload = payloadConfirmacaoManual("DINHEIRO", "RECIBO-2026-001");

        confirmarManualmente(tokenContextual, pedidoId, "\"1\"", "manual-1",
                        payload, status().isAccepted())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.version").value(2));
        confirmarManualmente(tokenContextual, pedidoId, "\"1\"", "manual-1",
                        payload, status().isAccepted())
                .andExpect(header().string("ETag", "\"2\""));
        confirmarManualmente(tokenContextual, pedidoId, "\"2\"", "manual-1",
                payloadConfirmacaoManual("PIX_MANUAL", "PIX-OUTRO"),
                status().isConflict());
        confirmarManualmente(tokenContextual, pedidoId, "\"1\"", "manual-2",
                payload, status().isPreconditionFailed());

        mvc.perform(get("/api/v1/inventory-holds/{id}", holdId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(header().string("ETag", "\"1\""));
        assertThat(jdbc.queryForObject("""
                select quantidade_reservada from produto_estacionamento where id = ?
                """, Integer.class, produtoId)).isZero();
        assertThat(jdbc.queryForObject("""
                select quantidade_vendida from produto_estacionamento where id = ?
                """, Integer.class, produtoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select status_conciliacao from confirmacao_manual_pedido where pedido_id = ?
                """, String.class, pedidoId)).isEqualTo("PENDENTE_CONCILIACAO");
        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao())
                    .isEqualTo("PEDIDO_CONFIRMADO_MANUALMENTE");
            assertThat(auditoria.getRecursoId()).isEqualTo(pedidoId);
        });
        assertThat(outbox.findAll()).anySatisfy(evento -> {
            assertThat(evento.getEventType()).isEqualTo("PedidoConfirmado");
            assertThat(evento.getAggregateType()).isEqualTo("PEDIDO");
            assertThat(evento.getAggregateId()).isEqualTo(pedidoId);
        });
    }

    @Test
    void confirmacaoManualExigePrecondicaoPermissaoEPedidoVigente() throws Exception {
        long pedidoId = criarPedidoPersistido("manual-rules");
        String payload = payloadConfirmacaoManual("DINHEIRO", "RECIBO-RULES");

        mvc.perform(post("/api/v1/orders/{id}/manual-confirmation", pedidoId)
                        .header("Authorization", tokenContextual)
                        .header("Idempotency-Key", "manual-no-version")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isPreconditionRequired());
        confirmarManualmente(tokenContextual, pedidoId, "\"1\"", "manual-field",
                payload.replaceFirst("\\{", "{\"status\":\"CONFIRMADO\","),
                status().isBadRequest());

        Usuario organizador = usuario("manual_organizador", PerfilUsuario.OPERADOR);
        long membershipId = criarMembership(organizador.getId(), tokenContextual);
        concederPapel(membershipId, "ORGANIZADOR");
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));
        confirmarManualmente(tokenOrganizador, pedidoId, "\"1\"", "manual-denied",
                payload, status().isForbidden());

        jdbc.update("""
                update pedido set created_at = now() - interval '1 hour',
                    expira_em = now() - interval '1 minute' where id = ?
                """, pedidoId);
        entityManager.clear();
        confirmarManualmente(tokenContextual, pedidoId, "\"1\"", "manual-expired",
                payload, status().isConflict());
    }

    @Test
    void emiteUmaCredencialPorUnidadeComIdempotenciaEAuditoria() throws Exception {
        long pedidoId = criarPedidoConfirmado("credential-units", 2);
        String payload = "{\"preferredMedium\":\"QR\"}";

        String primeira = emitirCredencial(
                        tokenContextual, pedidoId, "credential-unit-1", payload,
                        status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.eventId").value(eventoId))
                .andExpect(jsonPath("$.parkingFacilityId").value(patioId))
                .andExpect(jsonPath("$.orderId").value(pedidoId))
                .andExpect(jsonPath("$.unitNumber").value(1))
                .andExpect(jsonPath("$.publicCode").value(
                        org.hamcrest.Matchers.matchesPattern("CRD-[A-F0-9]{32}")))
                .andExpect(jsonPath("$.origin").value("PEDIDO"))
                .andExpect(jsonPath("$.right").value("ESTACIONAMENTO_EVENTO"))
                .andExpect(jsonPath("$.preferredMedium").value("QR"))
                .andExpect(jsonPath("$.vehiclePlate").doesNotExist())
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andReturn().getResponse().getContentAsString();
        long primeiraId = JsonPath.<Number>read(primeira, "$.id").longValue();

        String replay = emitirCredencial(
                        tokenContextual, pedidoId, "credential-unit-1", payload,
                        status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(replay, "$.id").longValue())
                .isEqualTo(primeiraId);
        emitirCredencial(tokenContextual, pedidoId, "credential-unit-1",
                "{\"preferredMedium\":\"RFID\"}", status().isConflict());
        emitirCredencial(tokenContextual, pedidoId, "credential-plate-multi",
                "{\"preferredMedium\":\"PLACA\"}", status().isConflict());

        mvc.perform(get("/api/v1/credentials/{id}", primeiraId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(primeiraId));
        mvc.perform(get("/api/v1/orders/{id}", pedidoId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));

        emitirCredencial(tokenContextual, pedidoId, "credential-unit-2", payload,
                        status().isCreated())
                .andExpect(jsonPath("$.unitNumber").value(2));
        mvc.perform(get("/api/v1/orders/{id}", pedidoId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATENDIDO"));
        emitirCredencial(tokenContextual, pedidoId, "credential-excess", payload,
                status().isConflict());

        assertThat(jdbc.queryForObject(
                "select count(*) from credencial_acesso where pedido_id = ?",
                Integer.class, pedidoId)).isEqualTo(2);
        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao()).isEqualTo("CREDENCIAL_EMITIDA");
            assertThat(auditoria.getEventoId()).isEqualTo(eventoId);
            assertThat(auditoria.getPatioId()).isEqualTo(patioId);
        });
        assertThat(outbox.findAll().stream().map(e -> e.getEventType()).toList())
                .contains("CredencialEmitida", "PedidoAtendido");
    }

    @Test
    void emissaoExigePedidoConfirmadoPermissaoOwnershipEContratoValido() throws Exception {
        long aguardandoId = criarPedidoPersistido("credential-waiting");
        String payload = "{\"preferredMedium\":\"QR\"}";

        emitirCredencial(tokenContextual, aguardandoId, "credential-waiting",
                payload, status().isConflict());
        mvc.perform(post("/api/v1/orders/{id}/credentials", aguardandoId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        emitirCredencial(tokenContextual, aguardandoId, "credential-field",
                "{\"preferredMedium\":\"QR\",\"status\":\"ATIVA\"}",
                status().isBadRequest());

        Usuario membro = usuario("credential_member", PerfilUsuario.OPERADOR);
        criarMembership(membro.getId(), tokenContextual);
        String tokenMembro = selecionar(organizacaoId,
                bearer(jwt.gerarToken(membro.getLogin(), "OPERADOR")));
        emitirCredencial(tokenMembro, aguardandoId, "credential-denied",
                payload, status().isForbidden());

        confirmarManualmente(tokenContextual, aguardandoId, "\"1\"",
                "manual-credential-owner",
                payloadConfirmacaoManual("PIX", "EV-credential-owner"),
                status().isAccepted());
        long confirmadoId = aguardandoId;
        String body = emitirCredencial(
                        tokenContextual, confirmadoId, "credential-owner", payload,
                        status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long credencialId = JsonPath.<Number>read(body, "$.id").longValue();

        Usuario organizador = usuario("credential_organizer", PerfilUsuario.OPERADOR);
        long membershipId = criarMembership(organizador.getId(), tokenContextual);
        concederPapel(membershipId, "ORGANIZADOR");
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));
        mvc.perform(get("/api/v1/credentials/{id}", credencialId)
                        .header("Authorization", tokenOrganizador))
                .andExpect(status().isNotFound());
    }

    @Test
    void geraQrImprevisivelUmaUnicaVezSemPersistirSegredo() throws Exception {
        long pedidoId = criarPedidoConfirmado("credential-qr", 1);
        String credencialBody = emitirCredencial(
                        tokenContextual, pedidoId, "credential-qr-unit",
                        "{\"preferredMedium\":\"QR\"}", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long credencialId = JsonPath.<Number>read(
                credencialBody, "$.id").longValue();

        String qrBody = gerarQr(tokenContextual, credencialId, "\"0\"",
                        status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.credentialId").value(credencialId))
                .andExpect(jsonPath("$.representationVersion").value(1))
                .andExpect(jsonPath("$.token").value(
                        org.hamcrest.Matchers.matchesPattern("KQ1-[A-Za-z0-9_-]{43}")))
                .andExpect(jsonPath("$.issuedAt").exists())
                .andExpect(jsonPath("$.expiresAt")
                        .value("2026-10-11T04:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        String tokenQr = JsonPath.read(qrBody, "$.token");

        assertThat(jdbc.queryForObject(
                "select token_hash from representacao_qr where credencial_id = ?",
                String.class, credencialId))
                .matches("[a-f0-9]{64}")
                .isNotEqualTo(tokenQr);
        assertThat(jdbc.queryForObject(
                "select count(*) from representacao_qr where token_hash = ?",
                Integer.class, tokenQr)).isZero();
        mvc.perform(get("/api/v1/credentials/{id}", credencialId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.qrVersion").value(1))
                .andExpect(jsonPath("$.token").doesNotExist());
        gerarQr(tokenContextual, credencialId, "\"0\"",
                status().isPreconditionFailed());

        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao()).isEqualTo("QR_CODE_GERADO");
            assertThat(auditoria.getRecursoId()).isEqualTo(credencialId);
        });
        assertThat(outbox.findAll().stream().map(e -> e.getEventType()).toList())
                .contains("QRCodeGerado");
    }

    @Test
    void geracaoQrExigePrecondicaoOwnershipMeioPermitidoEValidade() throws Exception {
        long pedidoId = criarPedidoConfirmado("credential-qr-rules", 1);
        String credencialBody = emitirCredencial(
                        tokenContextual, pedidoId, "credential-qr-rules-unit",
                        "{\"preferredMedium\":\"PLACA\"}", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long credencialId = JsonPath.<Number>read(
                credencialBody, "$.id").longValue();

        mvc.perform(post("/api/v1/credentials/{id}/qr-code", credencialId)
                        .header("Authorization", tokenContextual))
                .andExpect(status().isPreconditionRequired());
        gerarQr(tokenContextual, credencialId, "0", status().isBadRequest());
        gerarQr(tokenContextual, credencialId, "\"99\"",
                status().isPreconditionFailed());
        gerarQr(tokenContextual, credencialId, "\"0\"", status().isConflict());

        Usuario organizador = usuario("credential_qr_other", PerfilUsuario.OPERADOR);
        long membershipId = criarMembership(organizador.getId(), tokenContextual);
        concederPapel(membershipId, "ORGANIZADOR");
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));
        gerarQr(tokenOrganizador, credencialId, "\"0\"", status().isNotFound());
    }

    @Test
    void realizaCheckinQrIdempotenteComPresencaSessaoEOcupacao() throws Exception {
        long pedidoId = criarPedidoConfirmado("checkin-online", 1);
        String credencialBody = emitirCredencial(
                        tokenContextual, pedidoId, "checkin-credential",
                        "{\"preferredMedium\":\"QR\"}", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long credencialId = JsonPath.<Number>read(
                credencialBody, "$.id").longValue();
        String qrBody = gerarQr(tokenContextual, credencialId, "\"0\"",
                        status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrToken = JsonPath.read(qrBody, "$.token");
        prepararOperacaoEPermissaoCheckin();

        String payload = payloadCheckin(qrToken, eventoId, patioId, "PORTAO_NORTE");
        String primeiro = realizarCheckin(
                        tokenContextual, "checkin-online-1", payload, status().isOk())
                .andExpect(jsonPath("$.decision").value("AUTORIZADA"))
                .andExpect(jsonPath("$.reasonCode").value("AUTORIZADA"))
                .andExpect(jsonPath("$.credentialId").value(credencialId))
                .andExpect(jsonPath("$.presenceId").isNumber())
                .andExpect(jsonPath("$.parkingSessionId").isNumber())
                .andExpect(jsonPath("$.resultingOccupancy").value(1))
                .andExpect(jsonPath("$.barrierCommandRequested").value(false))
                .andReturn().getResponse().getContentAsString();
        long tentativaId = JsonPath.<Number>read(
                primeiro, "$.accessAttemptId").longValue();

        String replay = realizarCheckin(
                        tokenContextual, "checkin-online-1", payload, status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(replay, "$.accessAttemptId").longValue())
                .isEqualTo(tentativaId);
        realizarCheckin(tokenContextual, "checkin-online-2", payload, status().isOk())
                .andExpect(jsonPath("$.decision").value("RECUSADA"))
                .andExpect(jsonPath("$.reasonCode").value("ENTRADA_DUPLICADA"));

        assertThat(jdbc.queryForObject(
                "select status from credencial_acesso where id = ?",
                String.class, credencialId)).isEqualTo("DENTRO");
        assertThat(jdbc.queryForObject(
                "select count(*) from presenca where credencial_id = ? and status = 'DENTRO'",
                Integer.class, credencialId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from sessao_estacionamento where credencial_id = ? and status = 'ABERTA'",
                Integer.class, credencialId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select ocupacao_atual from alocacao_patio_evento where id = ?",
                Integer.class, alocacaoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from tentativa_acesso where credencial_id = ?",
                Integer.class, credencialId)).isEqualTo(2);
        assertThat(outbox.findAll().stream().map(e -> e.getEventType()).toList())
                .contains("AcessoAutorizado", "CheckinRealizado",
                        "MovimentacaoAberta", "OcupacaoAlterada", "AcessoRecusado");
        assertThat(auditorias.findAll().stream().map(a -> a.getAcao()).toList())
                .contains("CHECKIN_AUTORIZADO", "CHECKIN_RECUSADO");
    }

    @Test
    void recusaQrInvalidoELotacaoSemConsumirCredencial() throws Exception {
        long pedidoId = criarPedidoConfirmado("checkin-refusal", 1);
        String credencialBody = emitirCredencial(
                        tokenContextual, pedidoId, "checkin-refusal-credential",
                        "{\"preferredMedium\":\"QR\"}", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long credencialId = JsonPath.<Number>read(
                credencialBody, "$.id").longValue();
        String qrBody = gerarQr(tokenContextual, credencialId, "\"0\"",
                        status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrToken = JsonPath.read(qrBody, "$.token");
        prepararOperacaoEPermissaoCheckin();

        realizarCheckin(tokenContextual, "checkin-invalid",
                        payloadCheckin("KQ1-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                eventoId, patioId, "PORTAO_NORTE"), status().isOk())
                .andExpect(jsonPath("$.decision").value("RECUSADA"))
                .andExpect(jsonPath("$.reasonCode").value("CREDENCIAL_INVALIDA"))
                .andExpect(jsonPath("$.credentialId").doesNotExist());

        jdbc.update("""
                update alocacao_patio_evento
                set ocupacao_atual = capacidade_operacional where id = ?
                """, alocacaoId);
        entityManager.clear();
        realizarCheckin(tokenContextual, "checkin-full",
                        payloadCheckin(qrToken, eventoId, patioId, "PORTAO_NORTE"),
                        status().isOk())
                .andExpect(jsonPath("$.decision").value("RECUSADA"))
                .andExpect(jsonPath("$.reasonCode").value("PATIO_LOTADO"));

        assertThat(jdbc.queryForObject(
                "select status from credencial_acesso where id = ?",
                String.class, credencialId)).isEqualTo("ATIVA");
        assertThat(jdbc.queryForObject(
                "select count(*) from presenca where credencial_id = ?",
                Integer.class, credencialId)).isZero();
    }

    @Test
    void checkinExigePermissaoIdempotenciaEContratoEstrito() throws Exception {
        long pedidoId = criarPedidoConfirmado("checkin-contract", 1);
        String credencialBody = emitirCredencial(
                        tokenContextual, pedidoId, "checkin-contract-credential",
                        "{\"preferredMedium\":\"QR\"}", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long credencialId = JsonPath.<Number>read(
                credencialBody, "$.id").longValue();
        String qrBody = gerarQr(tokenContextual, credencialId, "\"0\"",
                        status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrToken = JsonPath.read(qrBody, "$.token");
        prepararOperacaoEPermissaoCheckin();
        String payload = payloadCheckin(qrToken, eventoId, patioId, "PORTAO_NORTE");

        Usuario membro = usuario("checkin_member", PerfilUsuario.OPERADOR);
        criarMembership(membro.getId(), tokenContextual);
        String tokenMembro = selecionar(organizacaoId,
                bearer(jwt.gerarToken(membro.getLogin(), "OPERADOR")));
        realizarCheckin(tokenMembro, "checkin-denied", payload,
                status().isForbidden());
        realizarCheckout(tokenMembro, "checkout-denied", payload,
                status().isForbidden());

        mvc.perform(post("/api/v1/check-ins")
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        realizarCheckin(tokenContextual, "checkin-extra-field",
                payload.replace("}", ",\"decision\":\"AUTORIZADA\"}"),
                status().isBadRequest());
        realizarCheckin(tokenContextual, "checkin-invalid-format",
                payloadCheckin("CRD-0123456789ABCDEF0123456789ABCDEF",
                        eventoId, patioId, "PORTAO_NORTE"),
                status().isBadRequest());
        assertThat(jdbc.queryForObject(
                "select count(*) from tentativa_acesso where organizacao_id = ?",
                Integer.class, organizacaoId)).isZero();
    }

    @Test
    void realizaCheckoutQrIdempotenteEncerrandoPresencaSessaoEOcupacao()
            throws Exception {
        long pedidoId = criarPedidoConfirmado("checkout-online", 1);
        String credencialBody = emitirCredencial(
                        tokenContextual, pedidoId, "checkout-credential",
                        "{\"preferredMedium\":\"QR\"}", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long credencialId = JsonPath.<Number>read(
                credencialBody, "$.id").longValue();
        String qrBody = gerarQr(tokenContextual, credencialId, "\"0\"",
                        status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrToken = JsonPath.read(qrBody, "$.token");
        prepararOperacaoEPermissaoCheckin();
        String payload = payloadCheckin(
                qrToken, eventoId, patioId, "PORTAO_SUL");
        realizarCheckin(tokenContextual, "checkout-entry", payload, status().isOk())
                .andExpect(jsonPath("$.decision").value("AUTORIZADA"));

        encerrarOperacao(tokenContextual, eventoId, "\"6\"",
                status().isConflict());

        String primeiro = realizarCheckout(
                        tokenContextual, "checkout-online-1", payload, status().isOk())
                .andExpect(jsonPath("$.decision").value("AUTORIZADA"))
                .andExpect(jsonPath("$.credentialId").value(credencialId))
                .andExpect(jsonPath("$.resultingOccupancy").value(0))
                .andExpect(jsonPath("$.credentialStatus").value("CONSUMIDA"))
                .andExpect(jsonPath("$.barrierCommandRequested").value(false))
                .andReturn().getResponse().getContentAsString();
        long tentativaId = JsonPath.<Number>read(
                primeiro, "$.accessAttemptId").longValue();
        String replay = realizarCheckout(
                        tokenContextual, "checkout-online-1", payload, status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(replay, "$.accessAttemptId").longValue())
                .isEqualTo(tentativaId);
        realizarCheckout(tokenContextual, "checkout-online-2", payload, status().isOk())
                .andExpect(jsonPath("$.decision").value("RECUSADA"))
                .andExpect(jsonPath("$.reasonCode").value("SAIDA_DUPLICADA"));
        encerrarOperacao(tokenContextual, eventoId, "\"6\"", status().isOk())
                .andExpect(header().string("ETag", "\"7\""))
                .andExpect(jsonPath("$.status").value("OPERACAO_ENCERRADA"));

        assertThat(jdbc.queryForObject(
                "select status from credencial_acesso where id = ?",
                String.class, credencialId)).isEqualTo("CONSUMIDA");
        assertThat(jdbc.queryForObject(
                "select status from presenca where credencial_id = ?",
                String.class, credencialId)).isEqualTo("FORA");
        assertThat(jdbc.queryForObject(
                "select status from sessao_estacionamento where credencial_id = ?",
                String.class, credencialId)).isEqualTo("ENCERRADA");
        assertThat(jdbc.queryForObject(
                "select ocupacao_atual from alocacao_patio_evento where id = ?",
                Integer.class, alocacaoId)).isZero();
        assertThat(outbox.findAll().stream().map(e -> e.getEventType()).toList())
                .contains("CheckoutRealizado", "MovimentacaoEncerrada",
                        "OcupacaoAlterada");
        assertThat(auditorias.findAll().stream().map(a -> a.getAcao()).toList())
                .contains("CHECKOUT_AUTORIZADO");
    }

    @Test
    void checkoutReativaCredencialQuandoEventoPermiteReentrada()
            throws Exception {
        eventos.atualizar(organizacaoId, eventoId, 0L,
                new AtualizarEventoRequest(
                        PoliticaReentrada.REENTRADA_PERMITIDA));
        long pedidoId = criarPedidoConfirmado("checkout-reentry", 1);
        String credencialBody = emitirCredencial(
                        tokenContextual, pedidoId, "checkout-reentry-credential",
                        "{\"preferredMedium\":\"QR\"}", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long credencialId = JsonPath.<Number>read(
                credencialBody, "$.id").longValue();
        String qrBody = gerarQr(tokenContextual, credencialId, "\"0\"",
                        status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrToken = JsonPath.read(qrBody, "$.token");
        prepararOperacaoEPermissaoCheckin();
        String payload = payloadCheckin(
                qrToken, eventoId, patioId, "PORTAO_SUL");
        realizarCheckin(tokenContextual, "reentry-entry", payload, status().isOk());

        String saida = realizarCheckout(
                        tokenContextual, "reentry-exit", payload, status().isOk())
                .andExpect(jsonPath("$.decision").value("AUTORIZADA"))
                .andExpect(jsonPath("$.credentialStatus").value("ATIVA"))
                .andReturn().getResponse().getContentAsString();
        realizarCheckin(tokenContextual, "reentry-second-entry", payload,
                        status().isOk())
                .andExpect(jsonPath("$.decision").value("AUTORIZADA"))
                .andExpect(jsonPath("$.resultingOccupancy").value(1));
        realizarCheckout(tokenContextual, "reentry-exit", payload, status().isOk())
                .andExpect(jsonPath("$.accessAttemptId").value(
                        JsonPath.<Number>read(
                                saida, "$.accessAttemptId").longValue()))
                .andExpect(jsonPath("$.credentialStatus").value("ATIVA"));
    }

    @Test
    void cancelaPedidoAguardandoELiberaHoldUmaUnicaVez() throws Exception {
        long pedidoId = criarPedidoPersistido("cancel-waiting");
        long reservaId = jdbc.queryForObject(
                "select reserva_inventario_id from pedido where id = ?",
                Long.class, pedidoId);
        long produtoId = jdbc.queryForObject(
                "select produto_id from reserva_inventario where id = ?",
                Long.class, reservaId);
        long versao = jdbc.queryForObject(
                "select version from pedido where id = ?", Long.class, pedidoId);
        String payload = "{\"reason\":\"DESISTENCIA_DO_COMPRADOR\"}";

        String body = cancelarPedido(
                        tokenContextual, pedidoId, "\"" + versao + "\"",
                        "cancel-waiting", payload, status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.orderId").value(pedidoId))
                .andExpect(jsonPath("$.orderStatus").value("CANCELADO"))
                .andExpect(jsonPath("$.refundRequired").value(false))
                .andExpect(jsonPath("$.releasedQuantity").value(1))
                .andExpect(jsonPath("$.blockedCredentials").value(0))
                .andExpect(jsonPath("$.reason")
                        .value("DESISTENCIA_DO_COMPRADOR"))
                .andReturn().getResponse().getContentAsString();
        long cancelamentoId = JsonPath.<Number>read(body, "$.id").longValue();

        String replay = cancelarPedido(
                        tokenContextual, pedidoId, "\"" + versao + "\"",
                        "cancel-waiting", payload, status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(replay, "$.id").longValue())
                .isEqualTo(cancelamentoId);
        cancelarPedido(tokenContextual, pedidoId, "\"" + versao + "\"",
                "cancel-waiting", "{\"reason\":\"OUTRO_MOTIVO\"}",
                status().isConflict());

        assertThat(jdbc.queryForObject(
                "select status from pedido where id = ?", String.class, pedidoId))
                .isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject(
                "select status from reserva_inventario where id = ?",
                String.class, reservaId)).isEqualTo("LIBERADA");
        assertThat(jdbc.queryForObject(
                "select quantidade_reservada from produto_estacionamento where id = ?",
                Integer.class, produtoId)).isZero();
        assertThat(outbox.findAll().stream().map(e -> e.getEventType()).toList())
                .contains("PedidoCancelado", "InventarioLiberado");
    }

    @Test
    void cancelaPedidoAtendidoBloqueiaQrELiberaVendaParaReembolso() throws Exception {
        long pedidoId = criarPedidoConfirmado("cancel-attended", 1);
        String credencialBody = emitirCredencial(
                        tokenContextual, pedidoId, "cancel-credential",
                        "{\"preferredMedium\":\"QR\"}", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long credencialId = JsonPath.<Number>read(
                credencialBody, "$.id").longValue();
        gerarQr(tokenContextual, credencialId, "\"0\"", status().isCreated());
        long versao = jdbc.queryForObject(
                "select version from pedido where id = ?", Long.class, pedidoId);

        cancelarPedido(tokenContextual, pedidoId, "\"" + versao + "\"",
                        "cancel-attended", "{\"reason\":\"EVENTO_NAO_UTILIZADO\"}",
                        status().isAccepted())
                .andExpect(jsonPath("$.orderStatus").value("EM_REEMBOLSO"))
                .andExpect(jsonPath("$.refundRequired").value(true))
                .andExpect(jsonPath("$.releasedQuantity").value(1))
                .andExpect(jsonPath("$.blockedCredentials").value(1));

        assertThat(jdbc.queryForObject(
                "select status from credencial_acesso where id = ?",
                String.class, credencialId)).isEqualTo("BLOQUEADA");
        assertThat(jdbc.queryForObject(
                "select revogada_em is not null from representacao_qr where credencial_id = ?",
                Boolean.class, credencialId)).isTrue();
        assertThat(jdbc.queryForObject("""
                select status from reserva_inventario
                where id = (select reserva_inventario_id from pedido where id = ?)
                """, String.class, pedidoId)).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("""
                select quantidade_vendida from produto_estacionamento
                where id = (select produto_id from item_pedido where pedido_id = ?)
                """, Integer.class, pedidoId)).isZero();
        assertThat(outbox.findAll().stream().map(e -> e.getEventType()).toList())
                .contains("PedidoCancelado", "CredencialBloqueada", "InventarioLiberado")
                .doesNotContain("ReembolsoSolicitado");
    }

    @Test
    void cancelamentoExigeContratoPrecondicaoPermissaoEOwnership() throws Exception {
        long pedidoId = criarPedidoPersistido("cancel-rules");
        String payload = "{\"reason\":\"DESISTENCIA_DO_COMPRADOR\"}";

        mvc.perform(post("/api/v1/orders/{id}/cancellation", pedidoId)
                        .header("Authorization", tokenContextual)
                        .header("Idempotency-Key", "cancel-missing-etag")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isPreconditionRequired());
        cancelarPedido(tokenContextual, pedidoId, "1", "cancel-bad-etag",
                payload, status().isBadRequest());
        cancelarPedido(tokenContextual, pedidoId, "\"99\"", "cancel-version",
                payload, status().isPreconditionFailed());
        cancelarPedido(tokenContextual, pedidoId, "\"1\"", "cancel-field",
                "{\"reason\":\"MOTIVO\",\"status\":\"CANCELADO\"}",
                status().isBadRequest());

        Usuario membro = usuario("cancel_member", PerfilUsuario.OPERADOR);
        criarMembership(membro.getId(), tokenContextual);
        String tokenMembro = selecionar(organizacaoId,
                bearer(jwt.gerarToken(membro.getLogin(), "OPERADOR")));
        cancelarPedido(tokenMembro, pedidoId, "\"1\"", "cancel-denied",
                payload, status().isForbidden());

        Usuario organizador = usuario("cancel_organizer", PerfilUsuario.OPERADOR);
        long membershipId = criarMembership(organizador.getId(), tokenContextual);
        concederPapel(membershipId, "ORGANIZADOR");
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));
        cancelarPedido(tokenOrganizador, pedidoId, "\"1\"", "cancel-other",
                payload, status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions publicarProduto(
            String token, Long produtoId, String ifMatch,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/parking-products/{id}/publication", produtoId)
                        .header("Authorization", token).header("If-Match", ifMatch)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(esperado);
    }

    private Long prepararVendaAberta(int quota, int quantidadeLote) throws Exception {
        return prepararProdutoPublicado(quota, quantidadeLote, true);
    }

    private Long prepararProdutoPublicado(
            int quota, int quantidadeLote, boolean abrirVendas) throws Exception {
        Long produtoId = criarProdutoBase(
                "Produto Hold " + quota + " " + quantidadeLote,
                "produto-hold-" + quota + "-" + quantidadeLote, quota);
        OffsetDateTime agora = OffsetDateTime.now().withNano(0);
        criarLote(tokenContextual, produtoId, "lote-hold-" + produtoId,
                payloadLote("Lote Hold", "45", "BRL", quantidadeLote, 1,
                        agora.minusDays(1).toString(), agora.plusDays(1).toString()),
                status().isCreated());
        publicarProduto(tokenContextual, produtoId, "\"0\"", status().isOk());
        String politica = jdbc.queryForObject(
                "select politica_reentrada from evento where id = ?",
                String.class, eventoId);
        if (politica == null) {
            long versao = jdbc.queryForObject(
                    "select version from evento where id = ?", Long.class, eventoId);
            eventos.atualizar(organizacaoId, eventoId, versao,
                    new AtualizarEventoRequest(PoliticaReentrada.ENTRADA_UNICA));
        }
        long versaoPublicacao = jdbc.queryForObject(
                "select version from evento where id = ?", Long.class, eventoId);
        eventos.publicar(
                organizacaoId, eventoId, versaoPublicacao,
                new PublicarEventoRequest(true));
        if (abrirVendas) {
            long versaoAbertura = jdbc.queryForObject(
                    "select version from evento where id = ?", Long.class, eventoId);
            eventos.abrirVendas(
                    organizacaoId, eventoId, versaoAbertura,
                    new AbrirVendasEventoRequest(null));
        }
        return produtoId;
    }

    private org.springframework.test.web.servlet.ResultActions criarHold(
            String token, String chave, String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/inventory-holds")
                        .header("Authorization", token).header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private org.springframework.test.web.servlet.ResultActions criarPedido(
            String token, String chave, String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/orders")
                        .header("Authorization", token).header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private long criarPedidoPersistido(String sufixo) throws Exception {
        Long produtoId = prepararVendaAberta(1, 1);
        String holdBody = criarHold(tokenContextual, "hold-order-" + sufixo,
                        payloadHold(produtoId, 1, null), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(holdBody, "$.id").longValue();
        String orderBody = criarPedido(tokenContextual, "order-" + sufixo,
                        payloadPedido(holdId, "DIRECT_WEB"), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(orderBody, "$.id").longValue();
    }

    private long criarPedidoDoProduto(Long produtoId, String sufixo) throws Exception {
        String holdBody = criarHold(tokenContextual, "hold-order-" + sufixo,
                        payloadHold(produtoId, 1, null), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(holdBody, "$.id").longValue();
        String orderBody = criarPedido(tokenContextual, "order-" + sufixo,
                        payloadPedido(holdId, "DIRECT_WEB"), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(orderBody, "$.id").longValue();
    }

    private long criarPedidoConfirmado(String sufixo, int quantidade) throws Exception {
        Long produtoId = prepararVendaAberta(quantidade, quantidade);
        String holdBody = criarHold(tokenContextual, "hold-" + sufixo,
                        payloadHold(produtoId, quantidade, null), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long holdId = JsonPath.<Number>read(holdBody, "$.id").longValue();
        String orderBody = criarPedido(tokenContextual, "order-" + sufixo,
                        payloadPedido(holdId, "DIRECT_WEB"), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pedidoId = JsonPath.<Number>read(orderBody, "$.id").longValue();
        confirmarManualmente(tokenContextual, pedidoId, "\"1\"", "manual-" + sufixo,
                payloadConfirmacaoManual("PIX", "EV-" + sufixo), status().isAccepted());
        return pedidoId;
    }

    private org.springframework.test.web.servlet.ResultActions emitirCredencial(
            String token, long pedidoId, String chave, String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/orders/{id}/credentials", pedidoId)
                        .header("Authorization", token).header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private org.springframework.test.web.servlet.ResultActions gerarQr(
            String token, long credencialId, String ifMatch,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/credentials/{id}/qr-code", credencialId)
                        .header("Authorization", token).header("If-Match", ifMatch))
                .andExpect(esperado);
    }

    private org.springframework.test.web.servlet.ResultActions cancelarPedido(
            String token, long pedidoId, String ifMatch, String chave,
            String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/orders/{id}/cancellation", pedidoId)
                        .header("Authorization", token).header("If-Match", ifMatch)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private org.springframework.test.web.servlet.ResultActions realizarCheckin(
            String token, String chave, String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/check-ins")
                        .header("Authorization", token)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private org.springframework.test.web.servlet.ResultActions realizarCheckout(
            String token, String chave, String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/check-outs")
                        .header("Authorization", token)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private org.springframework.test.web.servlet.ResultActions encerrarOperacao(
            String token, Long eventId, String ifMatch,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/events/{id}/operation-closing", eventId)
                        .header("Authorization", token).header("If-Match", ifMatch)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true,"
                                + "\"financialPendingAcknowledged\":true}"))
                .andExpect(esperado);
    }

    private void prepararOperacaoEPermissaoCheckin() throws Exception {
        jdbc.update("""
                update evento set inicio = now() - interval '1 hour',
                    fim = now() + interval '4 hours' where id = ?
                """, eventoId);
        jdbc.update("""
                update alocacao_patio_evento
                set inicio = now() - interval '1 hour',
                    fim = now() + interval '4 hours' where id = ?
                """, alocacaoId);
        jdbc.update("""
                update credencial_acesso
                set inicio_validade = now() - interval '1 hour',
                    fim_validade = now() + interval '4 hours'
                where evento_id = ?
                """, eventoId);
        jdbc.update("""
                update representacao_qr
                set expira_em = now() + interval '4 hours'
                where credencial_id in (
                    select id from credencial_acesso where evento_id = ?)
                """, eventoId);
        entityManager.clear();
        long versaoEvento = jdbc.queryForObject(
                "select version from evento where id = ?", Long.class, eventoId);
        eventos.encerrarVendas(
                organizacaoId, eventoId, versaoEvento,
                new PublicarEventoRequest(true));
        eventos.iniciarOperacao(
                organizacaoId, eventoId, versaoEvento + 1,
                new PublicarEventoRequest(true));
        long membershipId = jdbc.queryForObject("""
                select id from membro_organizacao
                where organizacao_id = ? and usuario_id = ? and status = 'ATIVO'
                """, Long.class, organizacaoId, admin.getId());
        concederPapel(membershipId, "SUPERVISOR");
    }

    private static String payloadCheckin(
            String qrToken, Long eventId, Long parkingFacilityId, String lane) {
        return """
                {"qrToken":"%s","eventId":%d,"parkingFacilityId":%d,
                 "lane":"%s"}
                """.formatted(qrToken, eventId, parkingFacilityId, lane);
    }

    private org.springframework.test.web.servlet.ResultActions confirmarManualmente(
            String token, long pedidoId, String ifMatch, String chave, String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/orders/{id}/manual-confirmation", pedidoId)
                        .header("Authorization", token).header("If-Match", ifMatch)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private static String payloadConfirmacaoManual(String meio, String evidencia) {
        return """
                {"paymentMethod":"%s","evidenceReference":"%s",
                 "reason":"RECEBIMENTO_PRESENCIAL_COMPROVADO"}
                """.formatted(meio, evidencia);
    }

    private static String payloadPedido(long holdId, String canal) {
        return """
                {"inventoryHoldId":%d,
                 "buyer":{"name":"Comprador Kaneko","email":"COMPRADOR@example.com",
                          "document":"123.456.789-09"},
                 "vehiclePlate":"ABC-1D23","channel":"%s"}
                """.formatted(holdId, canal);
    }

    private org.springframework.test.web.servlet.ResultActions liberarHold(
            String token, long holdId, String motivo,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/inventory-holds/{id}/release", holdId)
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + motivo + "\"}"))
                .andExpect(esperado);
    }

    private static String payloadHold(Long produtoId, int quantidade, String referencia) {
        String buyer = referencia == null ? "" : ",\"buyerReference\":\"" + referencia + "\"";
        return "{\"parkingProductId\":" + produtoId + ",\"quantity\":" + quantidade
                + ",\"channel\":\"DIRECT_WEB\"" + buyer + "}";
    }

    private Long criarProdutoBase(String nome, String chave, int quota) throws Exception {
        return criarProdutoBaseCategoria(nome, chave, quota, "COMUM");
    }

    private Long criarProdutoBaseCategoria(
            String nome, String chave, int quota, String categoria) throws Exception {
        String body = criarProduto(tokenContextual, eventoId, chave,
                payload(nome, alocacaoId, categoria, quota,
                        "2026-10-10T17:00:00-03:00", "2026-10-11T01:00:00-03:00"),
                status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private org.springframework.test.web.servlet.ResultActions criarLote(
            String token, Long produtoId, String chave, String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/parking-products/{id}/price-tiers", produtoId)
                        .header("Authorization", token).header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private static String payloadLote(
            String nome, String preco, String moeda, int quantidade, int prioridade,
            String inicio, String fim) {
        return """
                {"name":"%s","price":%s,"currency":"%s",
                 "salesStartsAt":"%s","salesEndsAt":"%s",
                 "quantity":%d,"priority":%d}
                """.formatted(nome, preco, moeda, inicio, fim, quantidade, prioridade);
    }

    private org.springframework.test.web.servlet.ResultActions criarProduto(
            String token, Long eventId, String chave, String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/events/{id}/parking-products", eventId)
                        .header("Authorization", token).header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private static String payload(
            String nome, Long allocationId, String categoria, int quota,
            String inicio, String fim) {
        return """
                {"name":"%s","category":"%s","parkingAllocationId":%d,
                 "accessStartsAt":"%s","accessEndsAt":"%s",
                 "right":"ESTACIONAMENTO_EVENTO","quota":%d,
                 "benefits":["ACESSO_PRIORITARIO"],
                 "restrictions":["VEICULO_LEVE"]}
                """.formatted(nome, categoria, allocationId, inicio, fim, quota);
    }

    private Long criarEvento(String nome, String chave) {
        return eventos.criar(organizacaoId, admin.getId(), chave, new CriarEventoRequest(
                nome, venueId, OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                OffsetDateTime.parse("2026-10-10T23:30:00-03:00"),
                "America/Sao_Paulo", null)).id();
    }

    private Long criarAlocacao(
            Long eventId, Long parkingId, int operacional, int vendavel,
            int reservada, String chave) {
        return criarAlocacao(
                organizacaoId, eventId, parkingId, operacional, vendavel, reservada, chave);
    }

    private Long criarAlocacao(
            Long orgId, Long eventId, Long parkingId, int operacional, int vendavel,
            int reservada, String chave) {
        return alocacoes.criar(orgId, admin.getId(), eventId, chave,
                new CriarAlocacaoPatioEventoRequest(
                        parkingId, OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                        operacional, vendavel, reservada)).id();
    }

    private long criarMembership(Long usuarioId, String token) throws Exception {
        return criarMembership(organizacaoId, usuarioId, token);
    }

    private long criarMembership(Long orgId, Long usuarioId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", orgId)
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuarioId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private void concederPapel(long membershipId, String papel) throws Exception {
        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments",
                        organizacaoId, membershipId)
                        .header("Authorization", tokenContextual).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"" + papel + "\"}"))
                .andExpect(status().isCreated());
    }

    private String selecionar(Long orgId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + orgId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return bearer(JsonPath.read(body, "$.token"));
    }

    private Long criarCidade(String nome) {
        var pais = paises.criar(new PaisRequest("Brasil " + nome, "BR", null, null, null, null, true));
        var estado = estados.criar(new EstadoRequest("Estado " + nome, "PR", pais.getId(), true));
        return cidades.criar(new CidadeRequest(nome, null, null, estado.getId(), true)).getId();
    }

    private Usuario usuario(String login, PerfilUsuario perfil) {
        return usuarios.criar(new UsuarioRequest(
                "Usuario Produto", login, "senha123", null, null, perfil));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
