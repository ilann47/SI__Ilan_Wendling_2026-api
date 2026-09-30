package com.kaneko.estacionamento.relatorio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class RelatorioTenantApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private RelatorioService service;
    @Autowired private com.kaneko.estacionamento.pessoas.ClienteRepository clientes;
    @Autowired private com.kaneko.estacionamento.fiscal.NotaServicoRepository notasServico;
    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach void setup() throws Exception {
        tenantA=tenant.criarTenantAdmin(); tenantB=tenant.criarTenantAdmin();
    }

    @Test
    void consolidaSomenteDadosDoTenantAtivo() throws Exception {
        criarDespesa(tenantA,100);
        criarDespesa(tenantB,900);
        String hoje=LocalDate.now().toString();
        String fim=LocalDate.now().plusDays(30).toString();

        mvc.perform(get("/api/relatorios/contas-a-vencer")
                .header("Authorization",tenantA.tokenContextual())
                .param("inicio",hoje).param("fim",fim))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aPagar.length()").value(1))
                .andExpect(jsonPath("$.totalAPagar").value(100));
        mvc.perform(get("/api/relatorios/faturamento")
                .header("Authorization",tenantA.tokenContextual()).param("inicio",hoje))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalFaturado").value(0));
        mvc.perform(get("/api/relatorios/patio")
                .header("Authorization",tenantA.tokenContextual())).andExpect(status().isOk());
        mvc.perform(get("/api/relatorios/estoque-minimo")
                .header("Authorization",tenantA.tokenContextual())).andExpect(status().isOk());
        mvc.perform(get("/api/relatorios/mensalistas-ativos")
                .header("Authorization",tenantA.tokenContextual())).andExpect(status().isOk());
    }

    @Test
    void faturamentoLegadoNaoIncluiNotasDeServicoDosTenants() throws Exception {
        var hoje=LocalDate.now();
        var legadoAntes=service.faturamento(null,hoje,hoje);
        criarNotaServico(tenantA,"NFS-REL-A",100);
        criarNotaServico(tenantB,"NFS-REL-B",900);
        org.assertj.core.api.Assertions.assertThat(service.faturamento(null,hoje,hoje).totalFaturado())
                .isEqualByComparingTo(legadoAntes.totalFaturado());
        mvc.perform(get("/api/relatorios/faturamento").header("Authorization",tenantA.tokenContextual())
                .param("inicio",hoje.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.servicos").value(100)).andExpect(jsonPath("$.totalFaturado").value(100));
        mvc.perform(get("/api/relatorios/faturamento").header("Authorization",tenantB.tokenContextual())
                .param("inicio",hoje.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.servicos").value(900)).andExpect(jsonPath("$.totalFaturado").value(900));
    }

    private void criarNotaServico(TenantFixture contexto,String numero,int valor) {
        var cliente=new com.kaneko.estacionamento.pessoas.Cliente();
        cliente.setOrganizacaoId(contexto.organizacaoId()); cliente.setNome("Cliente relatório");
        cliente.setDocumento("90000000001"); clientes.saveAndFlush(cliente);
        var nota=new com.kaneko.estacionamento.fiscal.NotaServico();
        nota.setOrganizacaoId(contexto.organizacaoId()); nota.setCliente(cliente); nota.setNumero(numero);
        nota.setDataEmissao(LocalDate.now()); nota.setValorServico(java.math.BigDecimal.valueOf(valor));
        nota.setValorTotal(java.math.BigDecimal.valueOf(valor));
        nota.setSituacao(com.kaneko.estacionamento.fiscal.SituacaoNotaServico.EMITIDA);
        notasServico.saveAndFlush(nota);
    }

    private void criarDespesa(TenantFixture fixture,int valor)throws Exception{
        mvc.perform(post("/api/contas-pagar-avulsas")
                .header("Authorization",fixture.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Despesa\",\"valorOriginal\":"+valor+","
                        +"\"dataVencimento\":\""+LocalDate.now().plusDays(10)+"\"}"))
                .andExpect(status().isCreated());
    }
}
