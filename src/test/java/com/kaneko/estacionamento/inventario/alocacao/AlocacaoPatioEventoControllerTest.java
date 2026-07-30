package com.kaneko.estacionamento.inventario.alocacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.auditoria.RegistroAuditoriaRepository;
import com.kaneko.estacionamento.common.security.JwtService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Transactional
class AlocacaoPatioEventoControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired VenueService venues;
    @Autowired InstalacoesService instalacoes;
    @Autowired EventoService eventos;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistroAuditoriaRepository auditorias;

    private Usuario admin;
    private Usuario gestor;
    private Usuario organizador;
    private Long organizacaoId;
    private Long venueId;
    private Long patioId;
    private Long eventoId;
    private String tokenGlobal;
    private String tokenContextual;

    @BeforeEach
    void setup() throws Exception {
        admin = usuario("alocacao_admin", PerfilUsuario.ADMIN);
        gestor = usuario("alocacao_gestor", PerfilUsuario.OPERADOR);
        organizador = usuario("alocacao_organizador", PerfilUsuario.OPERADOR);
        tokenGlobal = bearer(jwt.gerarToken(admin.getLogin(), "ADMIN"));
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Alocacoes Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        criarMembership(admin.getId(), tokenGlobal);
        tokenContextual = selecionar(organizacaoId, tokenGlobal);
        Long cidadeId = criarCidade("Curitiba Alocacoes");
        venueId = criarVenue(organizacaoId, cidadeId, "Arena Alocacoes");
        patioId = criarPatio(organizacaoId, cidadeId, "Patio Alocacoes", 100, 100, true);
        eventoId = criarEvento("Evento Alocacoes", "evento-alocacao-base");
    }

    @Test
    void alocaCapacidadeComAuditoriaEOutbox() throws Exception {
        String body = criarAlocacao(tokenContextual, eventoId, "alocacao-1",
                        payload(patioId, 80, 60, 20), status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.eventId").value(eventoId))
                .andExpect(jsonPath("$.parkingFacilityId").value(patioId))
                .andExpect(jsonPath("$.operationalCapacity").value(80))
                .andExpect(jsonPath("$.sellableCapacity").value(60))
                .andExpect(jsonPath("$.reservedCapacity").value(20))
                .andReturn().getResponse().getContentAsString();
        long alocacaoId = JsonPath.<Number>read(body, "$.id").longValue();

        var auditoria = auditorias.findAll().stream()
                .filter(a -> "CAPACIDADE_ALOCADA_AO_EVENTO".equals(a.getAcao())
                        && alocacaoId == a.getRecursoId())
                .findFirst().orElseThrow();
        assertThat(auditoria.getEventoId()).isEqualTo(eventoId);
        assertThat(auditoria.getPatioId()).isEqualTo(patioId);
        assertThat(jdbc.queryForObject("""
                        select count(*) from evento_outbox
                        where aggregate_type = 'ALOCACAO_PATIO_EVENTO'
                          and aggregate_id = ? and event_type = 'CapacidadeAlocadaAoEvento'
                        """, Long.class, alocacaoId)).isEqualTo(1L);
    }

    @Test
    void postEhIdempotenteEValidaHierarquiaDeCapacidade() throws Exception {
        String primeiro = criarAlocacao(tokenContextual, eventoId, "alocacao-idempotente",
                payload(patioId, 70, 60, 10), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String repetido = criarAlocacao(tokenContextual, eventoId, "alocacao-idempotente",
                payload(patioId, 70, 60, 10), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(repetido, "$.id").longValue())
                .isEqualTo(JsonPath.<Number>read(primeiro, "$.id").longValue());

        criarAlocacao(tokenContextual, eventoId, "alocacao-idempotente",
                payload(patioId, 70, 50, 10), status().isConflict());
        criarAlocacao(tokenContextual, criarEvento("Evento Capacidade Invalida", "evento-cap-invalida"),
                "alocacao-capacidade-invalida", payload(patioId, 70, 60, 20),
                status().isBadRequest());
    }

    @Test
    void impedeSobrealocacaoMasPermiteCompartilharSaldo() throws Exception {
        criarAlocacao(tokenContextual, eventoId, "alocacao-80",
                payload(patioId, 80, 70, 10), status().isCreated());
        Long segundoEvento = criarEvento("Segundo Evento", "evento-segundo");
        criarAlocacao(tokenContextual, segundoEvento, "alocacao-20",
                payload(patioId, 20, 20, 0), status().isCreated());
        Long terceiroEvento = criarEvento("Terceiro Evento", "evento-terceiro");
        criarAlocacao(tokenContextual, terceiroEvento, "alocacao-excedente",
                payload(patioId, 1, 1, 0), status().isConflict());
    }

    @Test
    void exigeInventoryManageETenantCoerente() throws Exception {
        long gestorMembership = criarMembership(gestor.getId(), tokenContextual);
        concederPapel(gestorMembership, "GESTOR");
        String tokenGestor = selecionar(organizacaoId,
                bearer(jwt.gerarToken(gestor.getLogin(), "OPERADOR")));
        criarAlocacao(tokenGestor, eventoId, "alocacao-gestor",
                payload(patioId, 30, 20, 10), status().isCreated());

        long organizadorMembership = criarMembership(organizador.getId(), tokenContextual);
        concederPapel(organizadorMembership, "ORGANIZADOR");
        String tokenOrganizador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(organizador.getLogin(), "OPERADOR")));
        Long eventoOrganizador = criarEvento("Evento do Organizador", "evento-organizador-alloc");
        criarAlocacao(tokenOrganizador, eventoOrganizador, "alocacao-sem-permissao",
                payload(patioId, 10, 10, 0), status().isForbidden());

        Long outraOrganizacao = organizacoes.criar(new CriarOrganizacaoRequest(
                "45.723.174/0001-10", "Outro Tenant Alocacoes Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-SP", "ENTERPRISE")).id();
        Long patioExterno = criarPatio(outraOrganizacao, criarCidade("Sao Paulo Alocacoes"),
                "Patio Externo", 100, 100, true);
        criarAlocacao(tokenContextual, eventoOrganizador, "alocacao-externa",
                payload(patioExterno, 10, 10, 0), status().isNotFound());
    }

    @Test
    void atualizaSomenteCapacidadesComIfMatch() throws Exception {
        String criado = criarAlocacao(tokenContextual, eventoId, "alocacao-patch",
                payload(patioId, 80, 60, 20), status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long alocacaoId = JsonPath.<Number>read(criado, "$.id").longValue();
        String patchBody = "{\"operationalCapacity\":70,\"sellableCapacity\":50,\"reservedCapacity\":20}";

        mvc.perform(patch("/api/v1/parking-allocations/{id}", alocacaoId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON).content(patchBody))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(patch("/api/v1/parking-allocations/{id}", alocacaoId)
                        .header("Authorization", tokenContextual).header("If-Match", "\"99\"")
                        .contentType(MediaType.APPLICATION_JSON).content(patchBody))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(patch("/api/v1/parking-allocations/{id}", alocacaoId)
                        .header("Authorization", tokenContextual).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(patchBody))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.operationalCapacity").value(70))
                .andExpect(jsonPath("$.sellableCapacity").value(50))
                .andExpect(jsonPath("$.reservedCapacity").value(20));
    }

    private org.springframework.test.web.servlet.ResultActions criarAlocacao(
            String token, Long eventId, String chave, String body,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/events/{id}/parking-allocations", eventId)
                        .header("Authorization", token).header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(esperado);
    }

    private static String payload(Long patio, int operacional, int vendavel, int reservada) {
        return """
                {"parkingFacilityId":%d,
                 "startsAt":"2026-10-10T17:00:00-03:00",
                 "endsAt":"2026-10-11T01:00:00-03:00",
                 "operationalCapacity":%d,"sellableCapacity":%d,"reservedCapacity":%d}
                """.formatted(patio, operacional, vendavel, reservada);
    }

    private Long criarEvento(String nome, String chave) {
        return eventos.criar(organizacaoId, admin.getId(), chave, new CriarEventoRequest(
                nome, venueId, OffsetDateTime.parse("2026-10-10T19:00:00-03:00"),
                OffsetDateTime.parse("2026-10-10T23:30:00-03:00"),
                "America/Sao_Paulo", null)).id();
    }

    private Long criarVenue(Long orgId, Long cidadeId, String nome) {
        return venues.criar(orgId, new CriarVenueRequest(
                nome, "Avenida do Evento", "500", null, "Centro", "80000-000",
                cidadeId, "America/Sao_Paulo")).id();
    }

    private Long criarPatio(Long orgId, Long cidadeId, String nome,
                            int fisica, int operacional, boolean ativo) {
        return instalacoes.criar(orgId, new CriarPatioInstalacaoRequest(
                nome, "Rua do Patio", null, null, "Centro", "80000-000", cidadeId,
                fisica, operacional, null, ativo)).id();
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
        return usuarios.criar(new UsuarioRequest("Usuario Alocacao", login, "senha123", null, null, perfil));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
