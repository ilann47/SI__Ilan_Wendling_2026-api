package com.kaneko.estacionamento.pagamento;

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
class PagamentoTenantApiTest {

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
    void tokenGlobalNaoAcessaCatalogosDePagamento() throws Exception {
        mvc.perform(get("/api/formas-pagamento")
                        .header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/condicoes-pagamento")
                        .header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonimoRecebe401() throws Exception {
        mvc.perform(get("/api/formas-pagamento"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/condicoes-pagamento"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void membershipSemPermissaoRecebe403() throws Exception {
        String login = "pagamento_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(
                tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String tokenOperador = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));

        mvc.perform(get("/api/formas-pagamento")
                        .header("Authorization", tokenOperador))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/formas-pagamento")
                        .header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pix\",\"descricao\":\"Teste\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteMesmoNomeEmTenantsDiferentesESegregaLeitura() throws Exception {
        long formaA = criarForma(tenantA, "Pix");
        long formaB = criarForma(tenantB, "Pix");

        mvc.perform(get("/api/formas-pagamento/{id}", formaB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/formas-pagamento")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(formaA));

        mvc.perform(put("/api/formas-pagamento/{id}", formaB)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Alterada\",\"descricao\":\"Invasao\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/formas-pagamento/{id}", formaB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/formas-pagamento/{id}", formaB)
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Pix"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void condicaoTambemIsolaLeituraEEscritaEntreTenants() throws Exception {
        long condicaoA = criarCondicao(tenantA, "A vista");
        long condicaoB = criarCondicao(tenantB, "A vista");

        mvc.perform(get("/api/condicoes-pagamento/{id}", condicaoB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/condicoes-pagamento/{id}", condicaoB)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Alterada\",\"numeroParcelas\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/condicoes-pagamento/{id}", condicaoB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/condicoes-pagamento")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(condicaoA));
        mvc.perform(get("/api/condicoes-pagamento/{id}", condicaoB)
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void condicaoNaoReferenciaFormaDeOutroTenant() throws Exception {
        long formaB = criarForma(tenantB, "Cartao B");
        String payload = """
                {
                  "nome": "A vista",
                  "numeroParcelas": 1,
                  "parcelas": [
                    {"numero": 1, "dias": 0, "percentual": 100.00, "formaPagamentoId": %d}
                  ]
                }
                """.formatted(formaB);

        mvc.perform(post("/api/condicoes-pagamento")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void payloadComOrganizacaoERejeitado() throws Exception {
        String payload = """
                {"nome":"Dinheiro", "descricao":"Especie", "organizationId":%d}
                """.formatted(tenantB.organizacaoId());
        mvc.perform(post("/api/formas-pagamento")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/formas-pagamento")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/formas-pagamento")
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(post("/api/condicoes-pagamento")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"numeroParcelas\":1,\"organizationId\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/condicoes-pagamento")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"X", "numeroParcelas":1,
                                 "parcelas":[{"numero":1,"dias":0,"percentual":100,
                                               "organizationId":1}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historicoSemTenantPermaneceInvisivel() throws Exception {
        Long formaId = jdbc.queryForObject("""
                INSERT INTO forma_pagamento
                    (nome, descricao, ativo, created_at, updated_at)
                VALUES ('Historica sem owner', 'Legado', true, now(), now())
                RETURNING id
                """, Long.class);
        Long condicaoId = jdbc.queryForObject("""
                INSERT INTO condicao_pagamento
                    (nome, numero_parcelas, percentual_juros, percentual_multa,
                     percentual_desconto, ativo, created_at, updated_at)
                VALUES ('Condicao historica', 1, 0, 0, 0, true, now(), now())
                RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO parcela_condicao_pagamento
                    (condicao_pagamento_id, numero, dias, percentual,
                     forma_pagamento_id, created_at, updated_at)
                VALUES (?, 1, 0, 100, ?, now(), now())
                """, condicaoId, formaId);

        mvc.perform(get("/api/formas-pagamento")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/formas-pagamento/{id}", formaId)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/condicoes-pagamento/{id}", condicaoId)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void criacaoRegistraAuditoriaNoTenantEAtorCorretos() throws Exception {
        long formaId = criarForma(tenantA, "Debito");
        mvc.perform(put("/api/formas-pagamento/{id}", formaId)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Debito atualizado\",\"descricao\":\"Teste\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/formas-pagamento/{id}", formaId)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNoContent());
        long condicaoId = criarCondicao(tenantA, "Parcelado");
        mvc.perform(put("/api/condicoes-pagamento/{id}", condicaoId)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Parcelado 2\",\"numeroParcelas\":1}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/condicoes-pagamento/{id}", condicaoId)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNoContent());

        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id = ?
                  AND ator_id = ?
                  AND ((recurso_tipo = 'FORMA_PAGAMENTO' AND recurso_id = ?)
                    OR (recurso_tipo = 'CONDICAO_PAGAMENTO' AND recurso_id = ?))
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), formaId, condicaoId);
        org.assertj.core.api.Assertions.assertThat(total).isEqualTo(6);
    }

    private long criarForma(TenantFixture fixture, String nome) throws Exception {
        String body = mvc.perform(post("/api/formas-pagamento")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"descricao\":\"Teste\"}"))
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
