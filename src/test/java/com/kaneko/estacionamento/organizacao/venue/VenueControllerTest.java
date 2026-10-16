package com.kaneko.estacionamento.organizacao.venue;

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
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
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
class VenueControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired JwtService jwt;
    @Autowired RegistroAuditoriaRepository auditorias;

    private Usuario admin;
    private Usuario operador;
    private Long organizacaoId;
    private Long cidadeId;
    private String tokenGlobal;
    private String tokenContextual;

    @BeforeEach
    void setup() throws Exception {
        admin = usuario("venue_admin", PerfilUsuario.ADMIN);
        operador = usuario("venue_operador", PerfilUsuario.OPERADOR);
        tokenGlobal = bearer(jwt.gerarToken(admin.getLogin(), "OPERADOR"));
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Venues Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        criarMembership(admin.getId(), tokenGlobal);
        tokenContextual = selecionar(organizacaoId, tokenGlobal);
        cidadeId = criarCidade(true, "Curitiba Venue");
    }

    @Test
    void criaVenueAtivoNoTenantComFusoVersionamentoEAuditoria() throws Exception {
        String body = criarVenue(tokenContextual, "Arena Kaneko", cidadeId,
                        "America/Sao_Paulo", status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.type").value("VENUE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"))
                .andReturn().getResponse().getContentAsString();
        long venueId = ((Number) JsonPath.read(body, "$.id")).longValue();

        var auditoria = auditorias.findAll().stream()
                .filter(a -> "UNIDADE_CRIADA".equals(a.getAcao()) && venueId == a.getRecursoId())
                .findFirst().orElseThrow();
        assertThat(auditoria.getOrganizacaoId()).isEqualTo(organizacaoId);
        assertThat(auditoria.getRecursoTipo()).isEqualTo("VENUE");
        assertThat(auditoria.getEstadoPosterior()).containsEntry("tipo", "VENUE");
    }

    @Test
    void exigeContextoEPermissaoENaoAceitaTenantNoPayload() throws Exception {
        criarVenue(tokenGlobal, "Venue Sem Contexto", cidadeId,
                "America/Sao_Paulo", status().isForbidden());

        criarMembership(operador.getId(), tokenContextual);
        String tokenOperador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(operador.getLogin(), "ADMIN")));
        criarVenue(tokenOperador, "Venue Sem Permissao", cidadeId,
                "America/Sao_Paulo", status().isForbidden());

        mvc.perform(post("/api/v1/venues")
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Venue Forjado", cidadeId, "America/Sao_Paulo")
                                .replace("{", "{\"organizationId\":999999,")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaNomeDuplicadoFusoInvalidoECidadeInativa() throws Exception {
        criarVenue(tokenContextual, "Arena Duplicada", cidadeId,
                "America/Sao_Paulo", status().isCreated());
        criarVenue(tokenContextual, " arena duplicada ", cidadeId,
                "America/Sao_Paulo", status().isConflict());
        criarVenue(tokenContextual, "Arena Fuso Invalido", cidadeId,
                "Sao_Paulo", status().isBadRequest());

        Long cidadeInativa = criarCidade(false, "Cidade Venue Inativa");
        criarVenue(tokenContextual, "Arena Cidade Inativa", cidadeInativa,
                "America/Sao_Paulo", status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions criarVenue(
            String token, String nome, Long cidade, String fuso,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mvc.perform(post("/api/v1/venues")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(nome, cidade, fuso)))
                .andExpect(esperado);
    }

    private static String payload(String nome, Long cidade, String fuso) {
        return """
                {"name":"%s","address":"Avenida do Evento","number":"500",
                 "district":"Centro","postalCode":"80000-000","cityId":%d,
                 "timeZone":"%s"}
                """.formatted(nome, cidade, fuso);
    }

    private void criarMembership(Long usuarioId, String token) throws Exception {
        mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuarioId + "}"))
                .andExpect(status().isCreated());
    }

    private String selecionar(Long orgId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + orgId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return bearer(JsonPath.read(body, "$.token"));
    }

    private Long criarCidade(boolean ativa, String nome) {
        var pais = paises.criar(new PaisRequest("Brasil " + nome, "BR", null, null, null, null, true));
        var estado = estados.criar(new EstadoRequest("Parana " + nome, "PR", pais.getId(), true));
        return cidades.criar(new CidadeRequest(nome, null, null, estado.getId(), ativa)).getId();
    }

    private Usuario usuario(String login, PerfilUsuario perfil) {
        return usuarios.criar(new UsuarioRequest("Usuario Venue", login, "senha123", null, null, perfil));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
