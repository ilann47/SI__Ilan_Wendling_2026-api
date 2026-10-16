package com.kaneko.estacionamento.ordensservico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.conveniencia.Servico;
import com.kaneko.estacionamento.conveniencia.ServicoRepository;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class OrdemServicoTenantApiTest {

    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired ClienteRepository clientes;
    @Autowired ServicoRepository servicos;
    @Autowired JdbcTemplate jdbc;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long clienteA;
    private Long clienteB;
    private Long servicoA;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        clienteA = criarCliente(tenantA.organizacaoId(), "A");
        clienteB = criarCliente(tenantB.organizacaoId(), "B");
        servicoA = criarServico(tenantA.organizacaoId());
    }

    @Test
    void criaIniciaEConcluiOrdemComConcorrenciaOtimista() throws Exception {
        String criada = criarOrdem("OS-001", clienteA, servicoA);
        long id = JsonPath.<Number>read(criada, "$.id").longValue();

        mvc.perform(post("/api/v1/service-orders/{id}/start", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/v1/service-orders/{id}/start", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.status").value("EM_EXECUCAO"));
        mvc.perform(post("/api/v1/service-orders/{id}/completion", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(post("/api/v1/service-orders/{id}/completion", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_receber WHERE organizacao_id=?",
                Integer.class, tenantA.organizacaoId())).isZero();
    }

    @Test
    void notaVinculadaFaturaOrdemConcluidaUmaUnicaVez() throws Exception {
        long id = JsonPath.<Number>read(criarOrdem("OS-FISCAL", clienteA, servicoA), "$.id").longValue();
        mvc.perform(post("/api/v1/service-orders/{id}/start", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/service-orders/{id}/completion", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isOk());

        String nota = mvc.perform(post("/api/notas-servico")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"NFS-OS-1\",\"clienteId\":" + clienteA
                                + ",\"servicoId\":" + servicoA
                                + ",\"ordemServicoId\":" + id
                                + ",\"valorServico\":100,\"aliquotaIss\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordemServicoId").value(id))
                .andReturn().getResponse().getContentAsString();
        long notaId = JsonPath.<Number>read(nota, "$.id").longValue();
        mvc.perform(post("/api/notas-servico/{id}/emissao", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("EMITIDA"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_receber WHERE nota_servico_id=?",
                Integer.class, notaId)).isEqualTo(1);
        mvc.perform(post("/api/notas-servico")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"NFS-OS-2\",\"clienteId\":" + clienteA
                                + ",\"ordemServicoId\":" + id + ",\"valorServico\":100}"))
                .andExpect(status().isConflict());
    }

    @Test
    void isolaTenantERecusaRelacionamentosCruzados() throws Exception {
        mvc.perform(post("/api/v1/service-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "os-cross")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OS-CROSS", clienteB, servicoA)))
                .andExpect(status().isNotFound());

        long id = JsonPath.<Number>read(criarOrdem("OS-ISOLADA", clienteA, servicoA), "$.id").longValue();
        mvc.perform(get("/api/v1/service-orders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void permiteEditarECancelarSomenteRascunhoComVersaoEsperada() throws Exception {
        String criada = criarOrdem("OS-EDIT", clienteA, servicoA);
        long id = JsonPath.<Number>read(criada, "$.id").longValue();

        mvc.perform(put("/api/v1/service-orders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"8\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OS-EDIT", clienteA, servicoA)))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(post("/api/v1/service-orders/{id}/cancellation", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void preparaNotaDaOrdemSemCobrarEEmiteSomenteUmaVez() throws Exception {
        long id = JsonPath.<Number>read(criarOrdem("OS-FLUXO", clienteA, servicoA), "$.id").longValue();
        String rota = "/api/v1/service-orders/" + id + "/service-note";
        mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header("Idempotency-Key", "nota-os").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"NFS-FLUXO\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/service-orders/{id}/start", id)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header(HttpHeaders.IF_MATCH, "\"0\"")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/service-orders/{id}/completion", id)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header(HttpHeaders.IF_MATCH, "\"1\"")).andExpect(status().isOk());
        String nota = mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header("Idempotency-Key", "nota-os").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"NFS-FLUXO\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clienteId").value(clienteA))
                .andExpect(jsonPath("$.valorTotal").value(100))
                .andExpect(jsonPath("$.situacao").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        long notaId = JsonPath.<Number>read(nota, "$.id").longValue();
        mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header("Idempotency-Key", "nota-os").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"NFS-FLUXO\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(notaId));
        mvc.perform(get("/api/v1/service-orders/{id}/documents", id)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nota.id").value(notaId))
                .andExpect(jsonPath("$.contas.length()").value(0));
        mvc.perform(post("/api/notas-servico/{id}/emissao", notaId)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())).andExpect(status().isOk());
        mvc.perform(post("/api/notas-servico/{id}/emissao", notaId)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/service-orders/{id}/documents", id)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contas.length()").value(1));
        mvc.perform(get("/api/v1/service-orders/{id}/documents", id)
                .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM nota_servico WHERE ordem_servico_id=?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void chamadasSimultaneasNaoDuplicamNotaNemCobranca() throws Exception {
        long id = JsonPath.<Number>read(criarOrdem("OS-RACE", clienteA, servicoA), "$.id").longValue();
        mvc.perform(post("/api/v1/service-orders/{id}/start", id)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header(HttpHeaders.IF_MATCH, "\"0\"")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/service-orders/{id}/completion", id)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header(HttpHeaders.IF_MATCH, "\"1\"")).andExpect(status().isOk());
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> gerar = () -> {
                start.await();
                String result = mvc.perform(post("/api/v1/service-orders/{id}/service-note", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "race-nota-os").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"NF-RACE\"}"))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
                return JsonPath.<Number>read(result, "$.id").longValue();
            };
            var a = pool.submit(gerar);
            var b = pool.submit(gerar);
            start.countDown();
            long notaId = a.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(b.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(notaId);
            var emitStart = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> emitir = () -> {
                emitStart.await();
                return mvc.perform(post("/api/notas-servico/{id}/emissao", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                        .andReturn().getResponse().getStatus();
            };
            var e1 = pool.submit(emitir);
            var e2 = pool.submit(emitir);
            emitStart.countDown();
            assertThat(java.util.List.of(e1.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    e2.get(20, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_receber WHERE nota_servico_id=?",
                    Integer.class, notaId)).isEqualTo(1);
        }
    }

    @Test
    void usuarioSemPapelNaoAcessaDocumentosNemGeraNota() throws Exception {
        long id = JsonPath.<Number>read(criarOrdem("OS-RBAC", clienteA, servicoA), "$.id").longValue();
        var usuario = tenant.criarUsuario("os_reader_" + UUID.randomUUID(),
                com.kaneko.estacionamento.usuario.PerfilUsuario.OPERADOR);
        tenant.criarMembership(tenantA.organizacaoId(), usuario.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(tenantA.organizacaoId(),
                tenant.autenticar(usuario.getLogin(), "senha123"));
        mvc.perform(get("/api/v1/service-orders/{id}/documents", id)
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/service-orders/{id}/service-note", id)
                .header(HttpHeaders.AUTHORIZATION, token).header("Idempotency-Key", "proibido")
                .contentType(MediaType.APPLICATION_JSON).content("{\"numero\":\"NF-RBAC\"}"))
                .andExpect(status().isForbidden());
    }

    private String criarOrdem(String numero, Long cliente, Long servico) throws Exception {
        return mvc.perform(post("/api/v1/service-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "create-" + numero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem(numero, cliente, servico)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.valorTotal").value(100))
                .andReturn().getResponse().getContentAsString();
    }

    private Long criarCliente(Long org, String sufixo) {
        Cliente cliente = new Cliente();
        cliente.setOrganizacaoId(org);
        cliente.setNome("Cliente OS " + sufixo);
        cliente.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        return clientes.saveAndFlush(cliente).getId();
    }

    private Long criarServico(Long org) {
        Servico servico = new Servico();
        servico.setOrganizacaoId(org);
        servico.setNome("Lavagem OS " + UUID.randomUUID());
        servico.setValorPadrao(new BigDecimal("100"));
        return servicos.saveAndFlush(servico).getId();
    }

    private static String ordem(String numero, Long cliente, Long servico) {
        return "{\"numero\":\"" + numero + "\",\"clienteId\":" + cliente
                + ",\"moeda\":\"BRL\",\"itens\":[{\"servicoId\":" + servico
                + ",\"quantidade\":1,\"valorUnitario\":100}]}";
    }
}
