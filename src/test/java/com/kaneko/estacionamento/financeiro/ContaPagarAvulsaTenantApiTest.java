package com.kaneko.estacionamento.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class ContaPagarAvulsaTenantApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach void setup() throws Exception {
        tenantA=tenant.criarTenantAdmin(); tenantB=tenant.criarTenantAdmin();
    }

    @Test
    void criaBaixaESegregaPorTenant() throws Exception {
        long despesa=criar(tenantA,"Aluguel");
        mvc.perform(get("/api/contas-pagar-avulsas/{id}",despesa)
                .header("Authorization",tenantB.tokenContextual())).andExpect(status().isNotFound());
        mvc.perform(post("/api/contas-pagar-avulsas/{id}/baixa",despesa)
                .header("Authorization",tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("PAGA"));
        Integer auditorias=jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria WHERE organizacao_id=?
                 AND recurso_id=? AND acao IN('DESPESA_AVULSA_CRIADA','DESPESA_AVULSA_BAIXADA')
                """,Integer.class,tenantA.organizacaoId(),despesa);
        assertThat(auditorias).isEqualTo(2);
    }

    private long criar(TenantFixture f,String descricao)throws Exception{
        String vencimento=LocalDate.now().plusDays(10).toString();
        String body=mvc.perform(post("/api/contas-pagar-avulsas")
                .header("Authorization",f.tokenContextual()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\""+descricao+"\",\"valorOriginal\":100,"
                        +"\"dataVencimento\":\""+vencimento+"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizacaoId").value(f.organizacaoId()))
                .andReturn().getResponse().getContentAsString();
        return ((Number)JsonPath.read(body,"$.id")).longValue();
    }
}
