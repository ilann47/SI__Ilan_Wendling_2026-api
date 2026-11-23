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
class VeiculoTenantApiTest {

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
        mvc.perform(get("/api/veiculos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/veiculos").header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());
    }

    @Test
    void normalizaPlacaPermiteMesmoValorEmTenantsESegregaLeitura() throws Exception {
        long clienteA = criarCliente(tenantA, "Cliente A", "11111111111");
        long clienteB = criarCliente(tenantB, "Cliente B", "22222222222");
        long veiculoA = criarVeiculo(tenantA, "abc-1d23", clienteA);
        long veiculoB = criarVeiculo(tenantB, "ABC-1D23", clienteB);

        mvc.perform(get("/api/veiculos/{id}", veiculoB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/veiculos").header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(veiculoA))
                .andExpect(jsonPath("$.content[0].placa").value("ABC-1D23"))
                .andExpect(jsonPath("$.content[0].organizacaoId")
                        .value(tenantA.organizacaoId()));
    }

    @Test
    void rejeitaClienteDeOutroTenantEAuditaCriacao() throws Exception {
        long clienteB = criarCliente(tenantB, "Cliente B", "33333333333");
        mvc.perform(post("/api/veiculos")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"ZZZ-9Z99\",\"clienteId\":" + clienteB + "}"))
                .andExpect(status().isNotFound());

        long clienteA = criarCliente(tenantA, "Cliente A", "44444444444");
        long veiculoA = criarVeiculo(tenantA, "zzz-9z99", clienteA);
        Integer auditorias = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id = ? AND ator_id = ?
                  AND acao = 'VEICULO_CRIADO' AND recurso_id = ?
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), veiculoA);
        assertThat(auditorias).isEqualTo(1);
    }

    private long criarCliente(TenantFixture fixture, String nome, String documento)
            throws Exception {
        String body = mvc.perform(post("/api/clientes")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"documento\":\""
                                + documento + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long criarVeiculo(TenantFixture fixture, String placa, long clienteId)
            throws Exception {
        String body = mvc.perform(post("/api/veiculos")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"" + placa + "\",\"clienteId\":" + clienteId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
