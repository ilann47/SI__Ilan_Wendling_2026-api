package com.kaneko.estacionamento.inventario.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.auditoria.RegistroAuditoriaRepository;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.common.outbox.EventoOutboxRepository;
import com.kaneko.estacionamento.evento.EventoService;
import com.kaneko.estacionamento.evento.dto.CriarEventoRequest;
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
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.venue.VenueService;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

    private org.springframework.test.web.servlet.ResultActions publicarProduto(
            String token, Long produtoId, String ifMatch,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/parking-products/{id}/publication", produtoId)
                        .header("Authorization", token).header("If-Match", ifMatch)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(esperado);
    }

    private Long criarProdutoBase(String nome, String chave, int quota) throws Exception {
        String body = criarProduto(tokenContextual, eventoId, chave,
                payload(nome, alocacaoId, "COMUM", quota,
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
