package com.kaneko.estacionamento.logistica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class TransportadoraTenantApiTest {

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
        mvc.perform(get("/api/transportadoras")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/transportadoras").header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());

        String login = "logistica_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/transportadoras").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transportadoras")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Negada\",\"documento\":\"00000000000000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteMesmoDocumentoEmTenantsDiferentesESegregaMutacoes() throws Exception {
        long idA = criar(tenantA, "Transportadora A", "12.345.678/0001-90", null);
        long idB = criar(tenantB, "Transportadora B", "12345678000190", null);

        mvc.perform(get("/api/transportadoras/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transportadoras")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idA));
        mvc.perform(put("/api/transportadoras/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Invasao\",\"documento\":\"12345678000190\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/transportadoras/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transportadoras/{id}", idB)
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Transportadora B"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void recusaCondicaoDeOutroTenantECampoDeOrganizacao() throws Exception {
        long condicaoB = criarCondicao(tenantB, "Prazo B");
        mvc.perform(post("/api/transportadoras")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Trans A\",\"documento\":\"11111111000111\","
                                + "\"condicaoPagamentoId\":" + condicaoB + "}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/transportadoras")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hostil\",\"documento\":\"22222222000122\","
                                + "\"organizationId\":" + tenantB.organizacaoId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historicoSemTenantPermaneceInvisivel() throws Exception {
        Long id = jdbc.queryForObject("""
                INSERT INTO transportadora
                    (nome, tipo, documento, ativo, created_at, updated_at)
                VALUES ('Historica', 'JURIDICA', '99999999000199', true, now(), now())
                RETURNING id
                """, Long.class);

        mvc.perform(get("/api/transportadoras").header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/transportadoras/{id}", id)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditaCriacaoAtualizacaoEInativacao() throws Exception {
        long id = criar(tenantA, "Auditada", "77777777000177", null);
        mvc.perform(put("/api/transportadoras/{id}", id)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Atualizada\",\"documento\":\"77777777000177\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/transportadoras/{id}", id)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNoContent());

        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id = ? AND ator_id = ? AND recurso_id = ?
                  AND acao IN ('TRANSPORTADORA_CRIADA', 'TRANSPORTADORA_ATUALIZADA',
                               'TRANSPORTADORA_DESATIVADA')
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), id);
        assertThat(total).isEqualTo(3);
    }

    private long criar(TenantFixture fixture, String nome, String documento, Long condicaoId)
            throws Exception {
        String condicao = condicaoId == null ? "" : ",\"condicaoPagamentoId\":" + condicaoId;
        String body = mvc.perform(post("/api/transportadoras")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"documento\":\""
                                + documento + "\"" + condicao + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long criarCondicao(TenantFixture fixture, String nome) throws Exception {
        String body = mvc.perform(post("/api/condicoes-pagamento")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"numeroParcelas\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
