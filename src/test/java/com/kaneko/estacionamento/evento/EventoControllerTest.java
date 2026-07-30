package com.kaneko.estacionamento.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.auditoria.RegistroAuditoriaRepository;
import com.kaneko.estacionamento.common.security.JwtService;
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
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.venue.VenueService;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Transactional
class EventoControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired VenueService venues;
    @Autowired InstalacoesService instalacoes;
    @Autowired AlocacaoPatioEventoService alocacoes;
    @Autowired ProdutoEstacionamentoService produtos;
    @Autowired LotePrecoService lotes;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistroAuditoriaRepository auditorias;

    private Usuario admin;
    private Usuario operador;
    private Usuario organizador;
    private Usuario gestor;
    private Long organizacaoId;
    private Long venueId;
    private Long patioId;
    private String tokenGlobal;
    private String tokenContextual;

    @BeforeEach
    void setup() throws Exception {
        admin = usuario("evento_admin", PerfilUsuario.ADMIN);
        operador = usuario("evento_operador", PerfilUsuario.OPERADOR);
        organizador = usuario("evento_organizador", PerfilUsuario.OPERADOR);
        gestor = usuario("evento_gestor", PerfilUsuario.OPERADOR);
        tokenGlobal = bearer(jwt.gerarToken(admin.getLogin(), "ADMIN"));
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Eventos Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        criarMembership(admin.getId(), tokenGlobal);
        tokenContextual = selecionar(organizacaoId, tokenGlobal);
        Long cidadeId = criarCidade("Curitiba Eventos");
        venueId = venues.criar(organizacaoId, new CriarVenueRequest(
                "Arena de Eventos", "Avenida Principal", "500", null,
                "Centro", "80000-000", cidadeId, "America/Sao_Paulo")).id();
        patioId = instalacoes.criar(organizacaoId, new CriarPatioInstalacaoRequest(
                "Patio de Eventos", "Avenida Principal", "510", null,
                "Centro", "80000-000", cidadeId, 100, 100, null, true)).id();
    }

    @Test
    void criaEventoRascunhoComChecklistAuditoriaEOutbox() throws Exception {
        String payload = payload("Final do Campeonato", venueId)
                .replace("}", ",\"externalId\":\"partner-event-123\"}");
        String body = criarEvento(tokenContextual, "evento-criacao-1", payload,
                        status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.venueId").value(venueId))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.externalId").value("partner-event-123"))
                .andExpect(jsonPath("$.configurationChecklist.ready").value(false))
                .andExpect(jsonPath("$.configurationChecklist.venue").value(true))
                .andExpect(jsonPath("$.configurationChecklist.parkingAllocation").value(false))
                .andExpect(jsonPath("$.configurationChecklist.parkingProduct").value(false))
                .andExpect(jsonPath("$.configurationChecklist.responsibleParty").value(false))
                .andExpect(jsonPath("$.configurationChecklist.policies").value(false))
                .andReturn().getResponse().getContentAsString();
        long eventoId = ((Number) JsonPath.read(body, "$.id")).longValue();

        var auditoria = auditorias.findAll().stream()
                .filter(a -> "EVENTO_CRIADO".equals(a.getAcao()) && eventoId == a.getRecursoId())
                .findFirst().orElseThrow();
        assertThat(auditoria.getOrganizacaoId()).isEqualTo(organizacaoId);
        assertThat(auditoria.getEventoId()).isEqualTo(eventoId);
        assertThat(auditoria.getEstadoPosterior()).containsEntry("status", "RASCUNHO");

        assertThat(jdbc.queryForObject(
                "select count(*) from evento_outbox where aggregate_id = ? and event_type = 'EventoCriado'",
                Long.class, eventoId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select payload ->> 'status' from evento_outbox where aggregate_id = ?", String.class, eventoId))
                .isEqualTo("RASCUNHO");

        criarEvento(tokenContextual, "evento-external-id-duplicado",
                payload("Outro Nome", venueId)
                        .replace("}", ",\"externalId\":\" PARTNER-EVENT-123 \"}"),
                status().isConflict());
    }

    @Test
    void repeteResultadoComMesmaChaveERejeitaPayloadDiferente() throws Exception {
        String primeiro = criarEvento(tokenContextual, "evento-idempotente", payload("Evento Idempotente", venueId),
                status().isCreated()).andReturn().getResponse().getContentAsString();
        String repetido = criarEvento(tokenContextual, "evento-idempotente", payload("Evento Idempotente", venueId),
                status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Number>read(repetido, "$.id").longValue())
                .isEqualTo(JsonPath.<Number>read(primeiro, "$.id").longValue());
        assertThat(jdbc.queryForObject("select count(*) from evento", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from evento_outbox", Long.class)).isEqualTo(1L);

        criarEvento(tokenContextual, "evento-idempotente", payload("Outro Evento", venueId),
                status().isConflict());
    }

    @Test
    void exigeChaveIdempotenteContextoEPermissao() throws Exception {
        mvc.perform(post("/api/v1/events")
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Sem Chave", venueId)))
                .andExpect(status().isBadRequest());

        criarEvento(tokenGlobal, "evento-sem-contexto", payload("Sem Contexto", venueId),
                status().isForbidden());

        criarMembership(operador.getId(), tokenContextual);
        String tokenOperador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(operador.getLogin(), "OPERADOR")));
        criarEvento(tokenOperador, "evento-sem-permissao", payload("Sem Permissao", venueId),
                status().isForbidden());
    }

    @Test
    void papelOrganizadorPodeCriarEvento() throws Exception {
        long membershipId = criarMembership(organizador.getId(), tokenContextual);
        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments",
                        organizacaoId, membershipId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"ORGANIZADOR\"}"))
                .andExpect(status().isCreated());
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));

        criarEvento(tokenOrganizador, "evento-organizador", payload("Evento do Organizador", venueId),
                status().isCreated());
    }

    @Test
    void rejeitaVenueDeOutroTenantDatasFusoECamposControlados() throws Exception {
        Long outraOrganizacao = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Tenant Eventos Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        Long outroVenue = venues.criar(outraOrganizacao, new CriarVenueRequest(
                "Arena Externa", "Rua Externa", null, null, "Centro", "01000-000",
                criarCidade("Sao Paulo Eventos"), "America/Sao_Paulo")).id();

        criarEvento(tokenContextual, "evento-outro-tenant", payload("Venue Externo", outroVenue),
                status().isNotFound());
        criarEvento(tokenContextual, "evento-periodo-invalido",
                payload("Periodo Invalido", venueId).replace(
                        "2026-10-10T23:30:00-03:00", "2026-10-10T18:00:00-03:00"),
                status().isBadRequest());
        criarEvento(tokenContextual, "evento-fuso-invalido",
                payload("Fuso Invalido", venueId).replace("America/Sao_Paulo", "Sao_Paulo"),
                status().isBadRequest());
        criarEvento(tokenContextual, "evento-tenant-forjado",
                payload("Tenant Forjado", venueId).replace("{", "{\"organizationId\":999999,"),
                status().isBadRequest());
    }

    @Test
    void publicaEventoProntoComChecklistAuditoriaEOutbox() throws Exception {
        Long eventoId = idEvento(criarEvento(
                tokenContextual, "evento-publicavel", payload("Evento Publicavel", venueId),
                status().isCreated()).andReturn().getResponse().getContentAsString());
        prepararProdutoPublicado(eventoId);
        long membershipGestor = criarMembership(gestor.getId(), tokenContextual);
        concederPapel(membershipGestor, "GESTOR");
        String tokenGestor = selecionar(organizacaoId,
                bearer(jwt.gerarToken(gestor.getLogin(), "OPERADOR")));

        publicarEvento(tokenGestor, eventoId, "\"0\"", status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.configurationChecklist.ready").value(true))
                .andExpect(jsonPath("$.configurationChecklist.venue").value(true))
                .andExpect(jsonPath("$.configurationChecklist.parkingAllocation").value(true))
                .andExpect(jsonPath("$.configurationChecklist.parkingProduct").value(true))
                .andExpect(jsonPath("$.configurationChecklist.responsibleParty").value(true))
                .andExpect(jsonPath("$.configurationChecklist.policies").value(true));

        assertThat(auditorias.findAll()).anySatisfy(auditoria -> {
            assertThat(auditoria.getAcao()).isEqualTo("EVENTO_PUBLICADO");
            assertThat(auditoria.getRecursoId()).isEqualTo(eventoId);
            assertThat(auditoria.getEstadoAnterior()).containsEntry("status", "RASCUNHO");
            assertThat(auditoria.getEstadoPosterior()).containsEntry("status", "PUBLICADO");
        });
        assertThat(jdbc.queryForObject("""
                select count(*) from evento_outbox
                where aggregate_type = 'EVENTO' and aggregate_id = ?
                  and event_type in ('EventoConfigurado', 'EventoPublicado')
                """, Long.class, eventoId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("""
                select aggregate_version from evento_outbox
                where aggregate_type = 'EVENTO' and aggregate_id = ?
                  and event_type = 'EventoConfigurado'
                """, Long.class, eventoId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                select aggregate_version from evento_outbox
                where aggregate_type = 'EVENTO' and aggregate_id = ?
                  and event_type = 'EventoPublicado'
                """, Long.class, eventoId)).isEqualTo(2L);
    }

    @Test
    void rejeitaPublicacaoIncompletaEProtegeVersaoEsperada() throws Exception {
        Long semAlocacao = idEvento(criarEvento(
                tokenContextual, "evento-sem-alocacao", payload("Sem Alocacao", venueId),
                status().isCreated()).andReturn().getResponse().getContentAsString());
        publicarEvento(tokenContextual, semAlocacao, "\"0\"", status().isConflict());

        Long eventoId = idEvento(criarEvento(
                tokenContextual, "evento-versao", payload("Evento Versao", venueId),
                status().isCreated()).andReturn().getResponse().getContentAsString());
        prepararProdutoPublicado(eventoId);
        mvc.perform(post("/api/v1/events/{id}/publication", eventoId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isPreconditionRequired());
        publicarEvento(tokenContextual, eventoId, "\"99\"",
                status().isPreconditionFailed());
        publicarEvento(tokenContextual, eventoId, "\"0\"", status().isOk());
        publicarEvento(tokenContextual, eventoId, "\"0\"",
                status().isPreconditionFailed());
    }

    @Test
    void organizadorNaoPodePublicarEvento() throws Exception {
        long membershipId = criarMembership(organizador.getId(), tokenContextual);
        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments",
                        organizacaoId, membershipId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"ORGANIZADOR\"}"))
                .andExpect(status().isCreated());
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));
        Long eventoId = idEvento(criarEvento(
                tokenContextual, "evento-nao-publicavel-organizador",
                payload("Evento Protegido", venueId), status().isCreated())
                .andReturn().getResponse().getContentAsString());

        publicarEvento(tokenOrganizador, eventoId, "\"0\"", status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions criarEvento(
            String token, String chave, String payload,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/events")
                        .header("Authorization", token)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(esperado);
    }

    private org.springframework.test.web.servlet.ResultActions publicarEvento(
            String token, Long eventoId, String ifMatch,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/events/{id}/publication", eventoId)
                        .header("Authorization", token).header("If-Match", ifMatch)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(esperado);
    }

    private void prepararProdutoPublicado(Long eventoId) {
        Long alocacaoId = alocacoes.criar(
                organizacaoId, admin.getId(), eventoId, "alocacao-" + eventoId,
                new CriarAlocacaoPatioEventoRequest(
                        patioId, OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                        100, 80, 20)).id();
        Long produtoId = produtos.criar(
                organizacaoId, admin.getId(), eventoId, "produto-" + eventoId,
                new CriarProdutoEstacionamentoRequest(
                        "Produto do Evento", CategoriaProduto.COMUM, alocacaoId,
                        OffsetDateTime.parse("2026-10-10T17:00:00-03:00"),
                        OffsetDateTime.parse("2026-10-11T01:00:00-03:00"),
                        DireitoProduto.ESTACIONAMENTO_EVENTO, 80,
                        List.of(), List.of())).id();
        lotes.criar(
                organizacaoId, admin.getId(), produtoId, "lote-" + produtoId,
                new CriarLotePrecoRequest(
                        "Lote do Evento", new BigDecimal("50.00"), "BRL",
                        OffsetDateTime.parse("2026-08-01T00:00:00-03:00"),
                        OffsetDateTime.parse("2026-09-01T00:00:00-03:00"),
                        80, 1));
        produtos.publicar(organizacaoId, produtoId, 0L, new PublicarProdutoRequest(true));
    }

    private static Long idEvento(String body) {
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private static String payload(String nome, Long venue) {
        return """
                {"name":"%s","venueId":%d,
                 "startsAt":"2026-10-10T19:00:00-03:00",
                 "endsAt":"2026-10-10T23:30:00-03:00",
                 "timeZone":"America/Sao_Paulo"}
                """.formatted(nome, venue);
    }

    private long criarMembership(Long usuarioId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuarioId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private void concederPapel(long membershipId, String papel) throws Exception {
        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments",
                        organizacaoId, membershipId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
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
        return usuarios.criar(new UsuarioRequest("Usuario Evento", login, "senha123", null, null, perfil));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
