package com.kaneko.estacionamento.instalacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Transactional
class InstalacoesPatioTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired OrganizacaoService organizacoes;
    @Autowired PaisService paises;
    @Autowired EstadoService estados;
    @Autowired CidadeService cidades;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistroAuditoriaRepository auditorias;

    private Usuario admin;
    private Usuario operador;
    private Long organizacaoId;
    private Long cidadeId;
    private String tokenAdmin;
    private String tokenContextual;

    @BeforeEach
    void setup() throws Exception {
        admin = usuario("inst_admin", PerfilUsuario.ADMIN);
        operador = usuario("inst_operador", PerfilUsuario.OPERADOR);
        tokenAdmin = bearer(jwt.gerarToken(admin.getLogin(), "OPERADOR"));
        organizacaoId = organizacoes.criar(new CriarOrganizacaoRequest(
                "12.345.678/0001-95", "Kaneko Instalacoes Ltda", null,
                "BRL", "America/Sao_Paulo", "BR-PR", "ENTERPRISE")).id();
        criarMembership(organizacaoId, admin.getId(), tokenAdmin);
        tokenContextual = selecionar(organizacaoId, tokenAdmin);
        cidadeId = criarCidade();
    }

    @Test
    void criaPatioNoTenantComCapacidadesVersionamentoEAuditoria() throws Exception {
        String body = criarPatio(tokenContextual, "Patio Norte", 100, 90, status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.organizationId").value(organizacaoId))
                .andExpect(jsonPath("$.physicalCapacity").value(100))
                .andExpect(jsonPath("$.operationalCapacity").value(90))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long patioId = ((Number) JsonPath.read(body, "$.id")).longValue();

        var auditoria = auditorias.findAll().stream()
                .filter(a -> "PATIO_CRIADO".equals(a.getAcao()) && patioId == a.getRecursoId())
                .findFirst().orElseThrow();
        assertThat(auditoria.getOrganizacaoId()).isEqualTo(organizacaoId);
        assertThat(auditoria.getPatioId()).isEqualTo(patioId);
        assertThat(auditoria.getEstadoPosterior()).containsEntry("capacidadeFisica", 100);
    }

    @Test
    void exigeContextoPermissaoENaoAceitaTenantDoCliente() throws Exception {
        criarPatio(tokenAdmin, "Patio Sem Contexto", 50, 40, status().isForbidden());

        criarMembership(organizacaoId, operador.getId(), tokenContextual);
        String tokenOperador = selecionar(organizacaoId,
                bearer(jwt.gerarToken(operador.getLogin(), "ADMIN")));
        criarPatio(tokenOperador, "Patio Sem Permissao", 50, 40, status().isForbidden());

        mvc.perform(post("/api/v1/parking-facilities")
                        .header("Authorization", tokenContextual).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Patio Tenant Forjado", 50, 40)
                                .replace("{", "{\"organizationId\":999999,")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaCapacidadeIncoerenteCidadeInexistenteENomeDuplicado() throws Exception {
        criarPatio(tokenContextual, "Patio Duplicado", 100, 80, status().isCreated());
        criarPatio(tokenContextual, " patio duplicado ", 100, 80, status().isConflict());
        criarPatio(tokenContextual, "Patio Excedido", 100, 101, status().isBadRequest());

        mvc.perform(post("/api/v1/parking-facilities")
                        .header("Authorization", tokenContextual).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Patio Cidade Invalida", 100, 80)
                                .replace("\"cityId\":" + cidadeId, "\"cityId\":999999")))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiLegadaPermaneceOperanteMasNaoEnxergaPatioTenantAware() throws Exception {
        String target = criarPatio(tokenContextual, "Patio Target", 80, 70, status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long targetId = ((Number) JsonPath.read(target, "$.id")).longValue();

        mvc.perform(get("/api/patios/{id}", targetId).header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());

        String legado = mvc.perform(post("/api/patios").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Patio Legado\",\"capacidadeVagas\":25,\"ativo\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long legadoId = ((Number) JsonPath.read(legado, "$.id")).longValue();
        var dados = jdbc.queryForMap(
                "SELECT organizacao_id, capacidade_operacional, version FROM patio WHERE id = ?", legadoId);
        assertThat(dados.get("organizacao_id")).isNull();
        assertThat(((Number) dados.get("capacidade_operacional")).intValue()).isEqualTo(25);
        assertThat(((Number) dados.get("version")).longValue()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions criarPatio(
            String token, String nome, int fisica, int operacional,
            org.springframework.test.web.servlet.ResultMatcher status) throws Exception {
        return mvc.perform(post("/api/v1/parking-facilities")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(payload(nome, fisica, operacional)))
                .andExpect(status);
    }

    private String payload(String nome, int fisica, int operacional) {
        return """
                {"name":"%s","address":"Rua das Instalacoes","number":"100",
                 "district":"Centro","postalCode":"80000-000","cityId":%d,
                 "physicalCapacity":%d,"operationalCapacity":%d,"active":true}
                """.formatted(nome, cidadeId, fisica, operacional);
    }

    private long criarMembership(long orgId, long userId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/organizations/{organizationId}/memberships", orgId)
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + userId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String selecionar(long orgId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + orgId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return bearer(JsonPath.read(body, "$.token"));
    }

    private Long criarCidade() {
        var pais = paises.criar(new PaisRequest("Brasil Instalacoes", "BR", null, null, null, null, true));
        var estado = estados.criar(new EstadoRequest("Parana Instalacoes", "PR", pais.getId(), true));
        return cidades.criar(new CidadeRequest("Curitiba Instalacoes", null, null, estado.getId(), true)).getId();
    }

    private Usuario usuario(String login, PerfilUsuario perfil) {
        return usuarios.criar(new UsuarioRequest("Usuario Instalacoes", login, "senha123", null, null, perfil));
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
