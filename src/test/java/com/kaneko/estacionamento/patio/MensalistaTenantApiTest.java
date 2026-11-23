package com.kaneko.estacionamento.patio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class MensalistaTenantApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
    }

    @Test
    void segregaContratosERejeitaReferenciasCruzadas() throws Exception {
        long clienteA = criarCliente(tenantA, "Cliente A", "11111111111");
        long veiculoA = criarVeiculo(tenantA, "AAA-1A11", clienteA);
        long clienteB = criarCliente(tenantB, "Cliente B", "22222222222");
        long veiculoB = criarVeiculo(tenantB, "BBB-2B22", clienteB);
        long contratoA = criarMensalista(tenantA, clienteA, veiculoA);

        mvc.perform(post("/api/mensalistas")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(clienteA, veiculoB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/mensalistas").header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/mensalistas/{id}", contratoA)
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void mensalidadeMantemOwnerDoContrato() throws Exception {
        long cliente = criarCliente(tenantA, "Cliente A", "33333333333");
        long veiculo = criarVeiculo(tenantA, "CCC-3C33", cliente);
        long contrato = criarMensalista(tenantA, cliente, veiculo);
        mvc.perform(post("/api/mensalistas/{id}/mensalidade", contrato)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizacaoId").value(tenantA.organizacaoId()));
        Long owner = jdbc.queryForObject("SELECT organizacao_id FROM conta_receber WHERE mensalista_id=?",
                Long.class, contrato);
        assertThat(owner).isEqualTo(tenantA.organizacaoId());
    }

    @Test
    void exigeContatoValidoParaIniciarOuAtivarSomenteCadastroFiscalBasico() throws Exception {
        long cliente = jdbc.queryForObject("""
                INSERT INTO cliente (organizacao_id,nome,tipo,documento,telefone,limite_credito_micros,origem_cadastro_fiscal)
                VALUES (?, 'Tomador do pátio', 'FISICA', '52998224725', 'entrada12345678901', 0, true)
                RETURNING id
                """, Long.class, tenantA.organizacaoId());
        long veiculo = criarVeiculo(tenantA, "DDD-4D44", cliente);
        String ativo = payload(cliente, veiculo);
        String token = tenantA.tokenContextual();
        mvc.perform(get("/api/clientes/{id}", cliente).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cadastroFiscalBasico").value(true));
        mvc.perform(post("/api/mensalistas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(ativo))
                .andExpect(status().isConflict());
        String inativo = ativo.replace("}", ",\"status\":\"SUSPENSO\"}");
        String json = mvc.perform(post("/api/mensalistas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(inativo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long contrato = ((Number) JsonPath.read(json, "$.id")).longValue();
        mvc.perform(put("/api/mensalistas/{id}", contrato).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(ativo))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/clientes/{id}", cliente).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Tomador atualizado\",\"documento\":\"52998224725\",\"email\":\"contato@example.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cadastroFiscalBasico").value(false));
        mvc.perform(put("/api/mensalistas/{id}", contrato).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(ativo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATIVO"));
        assertThat(jdbc.queryForObject("SELECT origem_cadastro_fiscal FROM cliente WHERE id=?", Boolean.class, cliente))
                .isTrue();
    }

    private long criarCliente(TenantFixture f, String nome, String documento) throws Exception {
        String body = mvc.perform(post("/api/clientes").header("Authorization", f.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\""+nome+"\",\"documento\":\""+documento+"\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number)JsonPath.read(body,"$.id")).longValue();
    }
    private long criarVeiculo(TenantFixture f, String placa, long cliente) throws Exception {
        String body = mvc.perform(post("/api/veiculos").header("Authorization", f.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placa\":\""+placa+"\",\"clienteId\":"+cliente+"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number)JsonPath.read(body,"$.id")).longValue();
    }
    private long criarMensalista(TenantFixture f, long cliente, long veiculo) throws Exception {
        String body = mvc.perform(post("/api/mensalistas").header("Authorization", f.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content(payload(cliente,veiculo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number)JsonPath.read(body,"$.id")).longValue();
    }
    private String payload(long cliente, long veiculo) {
        return "{\"clienteId\":"+cliente+",\"veiculoId\":"+veiculo+",\"valorMensal\":250}";
    }
}
