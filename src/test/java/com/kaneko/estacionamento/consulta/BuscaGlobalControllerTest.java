package com.kaneko.estacionamento.consulta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class BuscaGlobalControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantTestSupport tenant;
    TenantFixture tenantA;
    TenantFixture tenantB;
    Long clienteId;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        clienteId = inserirCliente(tenantA.organizacaoId(), "Mariana Souza", "12345678909");
        inserirCliente(tenantB.organizacaoId(), "Mariana de outro tenant", "98765432100");
    }

    @Test
    void buscaSomenteDadosPermitidosDoTenantAtivo() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "Mariana")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.grupos[0].grupo").value("Clientes"))
                .andExpect(jsonPath("$.grupos[0].resultados[0].titulo").value("Mariana Souza"))
                .andExpect(jsonPath("$.grupos[0].resultados[0].caminho").value("/app/clientes?detail=" + clienteId));
    }

    @Test
    void exigeTokenContextualETermoValido() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "Mariana"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/search").param("q", "M")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isBadRequest());
    }

    private Long inserirCliente(Long organizacaoId, String nome, String documento) {
        return jdbc.queryForObject("""
                INSERT INTO cliente (organizacao_id,nome,tipo,documento,limite_credito,ativo,created_at,updated_at)
                VALUES (?,?,'FISICA',?,0,true,now(),now())
                RETURNING id
                """, Long.class, organizacaoId, nome, documento);
    }
}
