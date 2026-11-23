package com.kaneko.estacionamento.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.support.tenant.*;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@TenantApiIntegrationTest
class FinanceiroTenantApiTest {
    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired FornecedorRepository fornecedores;
    @Autowired ClienteRepository clientes;
    @Autowired JdbcTemplate jdbc;

    @Test void filtrosTotaisEHistoricoRespeitamTenant() throws Exception {
        var a = tenant.criarTenantAdmin(); var b = tenant.criarTenantAdmin();
        for (String tipo : new String[]{"pagar", "receber"}) {
            long pessoa = pessoa(a, tipo);
            long primeira = criar(a, tipo, pessoa, 100);
            criar(a, tipo, pessoa, 200); criar(b, tipo, pessoa(b, tipo), 900);
            String path = "/api/contas-" + tipo;
            mvc.perform(get(path).header("Authorization", a.tokenContextual())
                            .param(tipo.equals("pagar") ? "fornecedorId" : "clienteId", "" + pessoa)
                            .param("situacao", "PENDENTE").param("origem", "MANUAL").param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
            mvc.perform(get(path + "/resumo").header("Authorization", a.tokenContextual()).param("origem", "MANUAL"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(2))
                    .andExpect(jsonPath("$.valorTotal").value(300)).andExpect(jsonPath("$.saldo").value(300));
            mvc.perform(get(path + "/resumo").header("Authorization", a.tokenContextual())
                            .param("dataVencimentoAte", LocalDate.now().toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(0));
            mvc.perform(get(path + "/" + primeira + "/baixas").header("Authorization", b.tokenContextual()))
                    .andExpect(status().isNotFound());
            mvc.perform(post(path + "/" + primeira + "/baixa").header("Authorization", b.tokenContextual())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":10}"))
                    .andExpect(status().isNotFound());
            mvc.perform(get(path).header("Authorization", a.tokenGlobal())).andExpect(status().isForbidden());
        }
    }

    @Test void baixaInvalidaNaoAlteraSaldoNemHistorico() throws Exception {
        var a = tenant.criarTenantAdmin();
        long id = criar(a, "receber", pessoa(a, "receber"), 100);
        mvc.perform(post("/api/contas-receber/{id}/baixa", id).header("Authorization", a.tokenContextual())
                        .header("Idempotency-Key", "maior-saldo").contentType(MediaType.APPLICATION_JSON).content("{\"valor\":101}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/contas-receber/{id}", id).header("Authorization", a.tokenContextual()))
                .andExpect(jsonPath("$.saldo").value(100)).andExpect(jsonPath("$.valorRecebido").value(0));
        mvc.perform(get("/api/contas-receber/{id}/baixas", id).header("Authorization", a.tokenContextual()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test void membershipSemPermissaoNaoConsultaNemBaixaPorChamadaDireta() throws Exception {
        var a = tenant.criarTenantAdmin();
        long id = criar(a, "pagar", pessoa(a, "pagar"), 100);
        var usuario = tenant.criarUsuario("finance_operador_" + UUID.randomUUID(),
                com.kaneko.estacionamento.usuario.PerfilUsuario.OPERADOR);
        tenant.criarMembership(a.organizacaoId(), usuario.getId(), a.tokenContextual());
        String global = tenant.autenticar(usuario.getLogin(), "senha123");
        String contextual = tenant.selecionarOrganizacao(a.organizacaoId(), global);
        mvc.perform(get("/api/contas-pagar/resumo").header("Authorization", contextual))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/contas-pagar/{id}/baixas", id).header("Authorization", contextual))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/contas-pagar/{id}/baixa", id).header("Authorization", contextual)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duasBaixasSimultaneasComMesmaChaveGeramUmLancamento() throws Exception {
        var a = tenant.criarTenantAdmin();
        for (String tipo : new String[]{"pagar", "receber"}) {
            long id = criar(a, tipo, pessoa(a, tipo), 100);
            CountDownLatch start = new CountDownLatch(1);
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Callable<Integer> task = () -> {
                    start.await(10, TimeUnit.SECONDS);
                    return mvc.perform(post("/api/contas-" + tipo + "/" + id + "/baixa")
                                    .header("Authorization", a.tokenContextual()).header("Idempotency-Key", "simultanea")
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":60}"))
                            .andReturn().getResponse().getStatus();
                };
                Future<Integer> first = executor.submit(task); Future<Integer> second = executor.submit(task);
                start.countDown();
                assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            }
            mvc.perform(get("/api/contas-" + tipo + "/" + id).header("Authorization", a.tokenContextual()))
                    .andExpect(jsonPath("$.saldo").value(40));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM baixa_financeira WHERE conta_" + tipo + "_id=?", Long.class, id)).isEqualTo(1);
        }
    }

    private long pessoa(TenantFixture org, String tipo) {
        String doc = UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        if (tipo.equals("pagar")) {
            Fornecedor f = new Fornecedor(); f.setOrganizacaoId(org.organizacaoId());
            f.setNome("Fornecedor financeiro"); f.setDocumento(doc); return fornecedores.saveAndFlush(f).getId();
        }
        Cliente c = new Cliente(); c.setOrganizacaoId(org.organizacaoId());
        c.setNome("Cliente financeiro"); c.setDocumento(doc); return clientes.saveAndFlush(c).getId();
    }

    private long criar(TenantFixture f, String tipo, long pessoa, int valor) throws Exception {
        String campo = tipo.equals("pagar") ? "fornecedorId" : "clienteId";
        String body = mvc.perform(post("/api/contas-" + tipo).header("Authorization", f.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"" + campo + "\":" + pessoa
                                + ",\"valorOriginal\":" + valor + ",\"dataVencimento\":\"" + LocalDate.now().plusDays(10) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
