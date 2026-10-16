package com.kaneko.estacionamento.patio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class TarifaTenantApiTest {

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
    void exigeContextoTenant() throws Exception {
        mvc.perform(get("/api/tarifas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tarifas").header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteDescricaoIgualEmTenantsESegregaLeitura() throws Exception {
        long tarifaA = criarTarifa(tenantA, "Evento");
        long tarifaB = criarTarifa(tenantB, "Evento");
        mvc.perform(get("/api/tarifas/{id}", tarifaB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tarifas").header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(tarifaA))
                .andExpect(jsonPath("$.content[0].organizacaoId")
                        .value(tenantA.organizacaoId()));
    }

    @Test
    void criacaoRegistraAuditoria() throws Exception {
        long tarifaA = criarTarifa(tenantA, "Auditada");
        Integer auditorias = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id=? AND ator_id=? AND acao='TARIFA_CRIADA'
                  AND recurso_id=?
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), tarifaA);
        assertThat(auditorias).isEqualTo(1);
    }

    private long criarTarifa(TenantFixture fixture, String descricao) throws Exception {
        String body = mvc.perform(post("/api/tarifas")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"" + descricao
                                + "\",\"valorHora\":10,\"valorDiaria\":50,"
                                + "\"valorMensalidade\":300}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
