package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.conveniencia.Marca;
import com.kaneko.estacionamento.conveniencia.MarcaRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.conveniencia.UnidadeMedida;
import com.kaneko.estacionamento.conveniencia.UnidadeMedidaRepository;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
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
class NotaEntradaTenantApiTest {

    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired FornecedorRepository fornecedores;
    @Autowired MarcaRepository marcas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ProdutoRepository produtos;
    @Autowired JdbcTemplate jdbc;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long fornecedorA;
    private Long fornecedorB;
    private Long produtoA;
    private Long localA;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        fornecedorA = criarFornecedor(tenantA.organizacaoId(), "A");
        fornecedorB = criarFornecedor(tenantB.organizacaoId(), "B");
        produtoA = criarProduto(tenantA.organizacaoId(), "A");
        localA = criarLocal(tenantA, "Recebimento");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void registroEConfirmacaoConcorrentesNaoDuplicamEstoqueOuTitulos() throws Exception {
        String body = payload("NF-RACE", fornecedorA, produtoA, localA);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> create = () -> {
                start.await();
                String result = mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "nota-race")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
                return JsonPath.<Number>read(result, "$.id").longValue();
            };
            var a = pool.submit(create); var b = pool.submit(create); start.countDown();
            long id = a.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(b.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(id);
            var confirming = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> confirm = () -> {
                confirming.await();
                return mvc.perform(post("/api/notas-entrada/{id}/confirmacao", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                        .andReturn().getResponse().getStatus();
            };
            var c = pool.submit(confirm); var d = pool.submit(confirm); confirming.countDown();
            assertThat(java.util.List.of(c.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    d.get(20, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("select count(*) from conta_pagar where nota_entrada_id=?", Integer.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from movimento_estoque where organizacao_id=?", Integer.class,
                    tenantA.organizacaoId())).isEqualTo(1);
        }
    }

    @Test
    void registroIdempotenteRecuperaNotaERecusaOutraCarga() throws Exception {
        String body = payload("NF-IDEMPOTENTE", fornecedorA, produtoA, localA);
        String criada = mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "nota-intento")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Number>read(criada, "$.id").longValue();
        mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "nota-intento")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "nota-intento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("NF-OUTRA", fornecedorA, produtoA, localA)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from nota_entrada where organizacao_id=?", Integer.class,
                tenantA.organizacaoId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from conta_pagar where organizacao_id=?", Integer.class,
                tenantA.organizacaoId())).isZero();
    }

    @Test
    void recusaNotaDuplicadaPorNumeroSerieEFornecedor() throws Exception {
        String body = "{\"numero\":\"12345\",\"modelo\":\"55\",\"serie\":\"2\","
                + "\"fornecedorId\":" + fornecedorA + ",\"localEstoqueId\":" + localA
                + ",\"dataEmissao\":\"2026-09-20\",\"dataChegada\":\"2026-09-21\","
                + "\"itens\":[{\"produtoId\":" + produtoA
                + ",\"quantidade\":10,\"valorUnitario\":5}]}";
        mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmaNotaNoTenantEGeraRazaoETitulo() throws Exception {
        String resposta = mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("NF-TENANT", fornecedorA, produtoA, localA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizacaoId").value(tenantA.organizacaoId()))
                .andExpect(jsonPath("$.localEstoqueId").value(localA))
                .andReturn().getResponse().getContentAsString();
        long notaId = JsonPath.<Number>read(resposta, "$.id").longValue();

        mvc.perform(post("/api/notas-entrada/{id}/confirmacao", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONFIRMADA"));

        mvc.perform(get("/api/v1/stock-balances")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString())
                        .param("localEstoqueId", localA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].quantidade").value(10));
        mvc.perform(get("/api/contas-pagar")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].notaEntradaId").value(notaId));

        mvc.perform(get("/api/notas-entrada/{id}/detalhes", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota.id").value(notaId))
                .andExpect(jsonPath("$.nota.itens[0].produtoId").value(produtoA))
                .andExpect(jsonPath("$.ordemCompra").doesNotExist())
                .andExpect(jsonPath("$.recebimento").doesNotExist())
                .andExpect(jsonPath("$.contasPagar.length()").value(1))
                .andExpect(jsonPath("$.contasPagar[0].notaEntradaId").value(notaId))
                .andExpect(jsonPath("$.movimentosEstoque.length()").value(1))
                .andExpect(jsonPath("$.movimentosEstoque[0].origemTipo")
                        .value("NOTA_ENTRADA"))
                .andExpect(jsonPath("$.movimentosEstoque[0].produtoId").value(produtoA));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM movimento_estoque
                WHERE organizacao_id=? AND origem_tipo='NOTA_ENTRADA'
                  AND origem_chave=? AND tipo='RECEBIMENTO'
                """, Integer.class, tenantA.organizacaoId(), notaId + ":1")).isEqualTo(1);
        assertThat(produtos.findById(produtoA).orElseThrow().getQuantidade())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ocultaOutroTenantERecusaReferenciasCruzadas() throws Exception {
        mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("NF-CROSS", fornecedorB, produtoA, localA)))
                .andExpect(status().isNotFound());

        String resposta = mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("NF-ISOLADA", fornecedorA, produtoA, localA)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long notaId = JsonPath.<Number>read(resposta, "$.id").longValue();
        mvc.perform(get("/api/notas-entrada/{id}", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notas-entrada/{id}/detalhes", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    private Long criarFornecedor(Long org, String sufixo) {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setOrganizacaoId(org);
        fornecedor.setNome("Fornecedor fiscal " + sufixo);
        fornecedor.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        return fornecedores.saveAndFlush(fornecedor).getId();
    }

    private Long criarProduto(Long org, String sufixo) {
        Marca marca = new Marca();
        marca.setOrganizacaoId(org);
        marca.setNome("Marca fiscal " + sufixo);
        marca = marcas.save(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(org);
        unidade.setNome("Unidade fiscal " + sufixo);
        unidade.setSigla("NF" + sufixo);
        unidade = unidades.save(unidade);
        Produto produto = new Produto();
        produto.setOrganizacaoId(org);
        produto.setNome("Produto fiscal " + sufixo);
        produto.setMarca(marca);
        produto.setUnidadeMedida(unidade);
        return produtos.saveAndFlush(produto).getId();
    }

    private Long criarLocal(TenantFixture fixture, String nome) throws Exception {
        String body = mvc.perform(post("/api/v1/stock-locations")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private String payload(String numero, Long fornecedor, Long produto, Long local) {
        return "{\"numero\":\"" + numero + "\",\"fornecedorId\":" + fornecedor
                + ",\"localEstoqueId\":" + local + ",\"itens\":[{\"produtoId\":"
                + produto + ",\"quantidade\":10,\"valorUnitario\":5}]}";
    }
}
