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

    @Test
    void preservaMilionesimosDaDespesaAteQuitacao() throws Exception {
        String body = mvc.perform(post("/api/contas-pagar-avulsas")
                .header("Authorization", tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"descricao":"Despesa exata","valorOriginal":12.000001,"valorJuros":0.000001,
                         "dataVencimento":"%s","numeroNota":"000120","modelo":"055","serie":"00A"}
                        """.formatted(LocalDate.now().plusDays(10))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.valorTotal").value(12.000002))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        baixar(id, "0.0000001").andExpect(status().isConflict());
        baixar(id, "0.000001").andExpect(status().isOk())
                .andExpect(jsonPath("$.valorPago").value(0.000001))
                .andExpect(jsonPath("$.situacao").value("PARCIAL"));
        baixar(id, "12.000001").andExpect(status().isOk())
                .andExpect(jsonPath("$.valorPago").value(12.000002))
                .andExpect(jsonPath("$.situacao").value("PAGA"))
                .andExpect(jsonPath("$.numeroNota").value("000120"))
                .andExpect(jsonPath("$.modelo").value("055"))
                .andExpect(jsonPath("$.serie").value("00A"));
        assertThat(jdbc.queryForObject(
                "select valor_pago_micros from contas_pagar_avulsa where id=?", Long.class, id))
                .isEqualTo(12000002L);
    }

    private org.springframework.test.web.servlet.ResultActions baixar(long id, String valor) throws Exception {
        return mvc.perform(post("/api/contas-pagar-avulsas/{id}/baixa", id)
                .header("Authorization", tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":" + valor + "}"));
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
