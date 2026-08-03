package com.kaneko.estacionamento.pessoas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class ClienteTenantApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;

    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
    }

    @Test
    void exigeContextoEPermissao() throws Exception {
        mvc.perform(get("/api/clientes"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clientes").header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());

        String login = "cliente_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(
                tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String tokenOperador = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/clientes").header("Authorization", tokenOperador))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/clientes")
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Negado\",\"documento\":\"00000000000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteMesmoDocumentoEmTenantsDiferentesESegregaLeitura() throws Exception {
        long clienteA = criarCliente(tenantA, "Cliente A", "123.456.789-01", null);
        long clienteB = criarCliente(tenantB, "Cliente B", "12345678901", null);

        mvc.perform(get("/api/clientes/{id}", clienteB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(clienteA));
        mvc.perform(put("/api/clientes/{id}", clienteB)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Invasao\",\"documento\":\"12345678901\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/clientes/{id}", clienteB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/{id}", clienteB)
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cliente B"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void naoAceitaCondicaoPagamentoDeOutroTenant() throws Exception {
        long condicaoB = criarCondicao(tenantB, "A vista B");

        mvc.perform(post("/api/clientes")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Cliente A", "documento":"11111111111",
                                 "condicaoPagamentoId":%d}
                                """.formatted(condicaoB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void historicoSemTenantPermaneceInvisivel() throws Exception {
        Long clienteId = jdbc.queryForObject("""
                INSERT INTO cliente
                    (nome, tipo, documento, limite_credito, ativo, created_at, updated_at)
                VALUES ('Historico', 'FISICA', '99999999999', 0, true, now(), now())
                RETURNING id
                """, Long.class);

        mvc.perform(get("/api/clientes")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/clientes/{id}", clienteId)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void payloadComOrganizacaoERejeitado() throws Exception {
        mvc.perform(post("/api/clientes")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Cliente auditado", "documento":"77777777777",
                                 "organizationId":%d}
                                """.formatted(tenantB.organizacaoId())))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/clientes")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/clientes")
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void criacaoAuditaTenantEAtorReais() throws Exception {
        long clienteId = criarCliente(
                tenantA, "Cliente auditado", "77777777777", null);
        mvc.perform(put("/api/clientes/{id}", clienteId)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Cliente atualizado\",\"documento\":\"77777777777\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/clientes/{id}", clienteId)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNoContent());

        Integer auditorias = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id = ? AND ator_id = ?
                  AND acao IN ('CLIENTE_CRIADO', 'CLIENTE_ATUALIZADO', 'CLIENTE_DESATIVADO')
                  AND recurso_id = ?
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), clienteId);
        assertThat(auditorias).isEqualTo(3);
    }

    private long criarCliente(
            TenantFixture fixture,
            String nome,
            String documento,
            Long condicaoPagamentoId) throws Exception {
        String condicao = condicaoPagamentoId == null
                ? "" : ",\"condicaoPagamentoId\":" + condicaoPagamentoId;
        String body = mvc.perform(post("/api/clientes")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"documento\":\""
                                + documento + "\"" + condicao + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long criarCondicao(TenantFixture fixture, String nome) throws Exception {
        String body = mvc.perform(post("/api/condicoes-pagamento")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"numeroParcelas\":1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
