package com.kaneko.estacionamento.patio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

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
class MovimentacaoTenantApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach void setup() throws Exception {
        tenantA=tenant.criarTenantAdmin(); tenantB=tenant.criarTenantAdmin();
    }

    @Test
    void entradaSaidaESegregacaoRespeitamTenant() throws Exception {
        long cliente=criarCliente(tenantA,"11111111111");
        long veiculo=criarVeiculo(tenantA,"AAA-1A11",cliente);
        criarTarifa(tenantA);
        long patio=criarPatio(tenantA);
        String body=mvc.perform(post("/api/movimentacoes/entrada")
                .header("Authorization",tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veiculoId\":"+veiculo+",\"patioId\":"+patio+"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizacaoId").value(tenantA.organizacaoId()))
                .andReturn().getResponse().getContentAsString();
        long id=((Number)JsonPath.read(body,"$.id")).longValue();
        mvc.perform(get("/api/movimentacoes/{id}",id)
                .header("Authorization",tenantB.tokenContextual())).andExpect(status().isNotFound());
        mvc.perform(post("/api/movimentacoes/{id}/saida",id)
                .header("Authorization",tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FECHADO"));

        long veiculoCancelado=criarVeiculo(tenantA,"BBB-2B22",cliente);
        String cancelBody=mvc.perform(post("/api/movimentacoes/entrada")
                .header("Authorization",tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veiculoId\":"+veiculoCancelado+",\"patioId\":"+patio+"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long cancelId=((Number)JsonPath.read(cancelBody,"$.id")).longValue();
        mvc.perform(post("/api/movimentacoes/{id}/cancelamento",cancelId)
                .header("Authorization",tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADO"));

        Integer auditorias=jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id=? AND ator_id=? AND recurso_tipo='MOVIMENTACAO'
                  AND acao IN ('MOVIMENTACAO_ABERTA','MOVIMENTACAO_FECHADA','MOVIMENTACAO_CANCELADA')
                """,Integer.class,tenantA.organizacaoId(),tenantA.usuarioId());
        assertThat(auditorias).isEqualTo(4);
    }

    private long criarCliente(TenantFixture f,String doc)throws Exception{
        String b=mvc.perform(post("/api/clientes").header("Authorization",f.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cliente\",\"documento\":\""+doc+"\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number)JsonPath.read(b,"$.id")).longValue();
    }
    private long criarVeiculo(TenantFixture f,String placa,long cliente)throws Exception{
        String b=mvc.perform(post("/api/veiculos").header("Authorization",f.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content("{\"placa\":\""+placa+"\",\"clienteId\":"+cliente+"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number)JsonPath.read(b,"$.id")).longValue();
    }
    private void criarTarifa(TenantFixture f)throws Exception{
        mvc.perform(post("/api/tarifas").header("Authorization",f.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Avulso\",\"valorHora\":10,\"valorDiaria\":50,\"valorMensalidade\":300}"))
                .andExpect(status().isCreated());
    }
    private long criarPatio(TenantFixture f){
        return jdbc.queryForObject("""
                INSERT INTO patio(organizacao_id,nome,capacidade_vagas,capacidade_operacional,
                 ativo,version,created_at,updated_at) VALUES(?, ?, 100,100,true,0,now(),now()) RETURNING id
                """,Long.class,f.organizacaoId(),"Patio "+f.organizacaoId());
    }
}
