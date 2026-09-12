package com.kaneko.estacionamento.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class OrdemCompraTenantApiTest {

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
        localA = criarLocal(tenantA, "Recebimento compras");
    }

    @Test
    void criaAprovaERecebeParcialmenteSemSobreRecebimento() throws Exception {
        String criada = mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-criar-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-001", fornecedorA, produtoA, "5")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andReturn().getResponse().getContentAsString();
        long ordemId = JsonPath.<Number>read(criada, "$.id").longValue();
        long itemId = JsonPath.<Number>read(criada, "$.itens[0].id").longValue();

        mvc.perform(post("/api/v1/purchase-orders/{id}/approval", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/v1/purchase-orders/{id}/approval", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.status").value("APROVADA"));

        String recebimento = receber(ordemId, itemId, "2", "\"1\"", "receber-1")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.itens[0].quantidade").value(2))
                .andReturn().getResponse().getContentAsString();
        long recebimentoId = JsonPath.<Number>read(recebimento, "$.id").longValue();

        receber(ordemId, itemId, "2", "\"1\"", "receber-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(recebimentoId));
        mvc.perform(get("/api/v1/purchase-orders/{id}", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARCIALMENTE_RECEBIDA"))
                .andExpect(jsonPath("$.itens[0].quantidadePendente").value(3));

        receber(ordemId, itemId, "4", "\"2\"", "receber-excesso")
                .andExpect(status().isConflict());
        receber(ordemId, itemId, "3", "\"2\"", "receber-2")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""));
        mvc.perform(get("/api/v1/purchase-orders/{id}", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEBIDA"));

        assertThat(jdbc.queryForObject("""
                SELECT quantidade FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, BigDecimal.class, tenantA.organizacaoId(), produtoA, localA))
                .isEqualByComparingTo("5");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM movimento_estoque
                WHERE organizacao_id=? AND origem_tipo='ORDEM_COMPRA'
                """, Integer.class, tenantA.organizacaoId())).isEqualTo(2);
    }

    @Test
    void isolaTenantReferenciasEListagem() throws Exception {
        mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-cross")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-CROSS", fornecedorB, produtoA, "1")))
                .andExpect(status().isNotFound());

        String criada = mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-isolada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-ISOLADA", fornecedorA, produtoA, "1")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ordemId = JsonPath.<Number>read(criada, "$.id").longValue();
        mvc.perform(get("/api/v1/purchase-orders/{id}", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void notaVinculadaAoRecebimentoGeraFinanceiroSemDuplicarEstoque() throws Exception {
        String criada = mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-fiscal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-FISCAL", fornecedorA, produtoA, "5")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ordemId = JsonPath.<Number>read(criada, "$.id").longValue();
        long itemId = JsonPath.<Number>read(criada, "$.itens[0].id").longValue();
        mvc.perform(post("/api/v1/purchase-orders/{id}/approval", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk());
        String recebida = receber(ordemId, itemId, "5", "\"1\"", "receber-fiscal")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long recebimentoId = JsonPath.<Number>read(recebida, "$.id").longValue();

        String nota = mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"NF-OC-1\",\"fornecedorId\":" + fornecedorA
                                + ",\"localEstoqueId\":" + localA
                                + ",\"recebimentoCompraId\":" + recebimentoId
                                + ",\"itens\":[{\"produtoId\":" + produtoA
                                + ",\"quantidade\":5,\"valorUnitario\":10}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recebimentoCompraId").value(recebimentoId))
                .andReturn().getResponse().getContentAsString();
        long notaId = JsonPath.<Number>read(nota, "$.id").longValue();
        mvc.perform(post("/api/notas-entrada/{id}/confirmacao", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CONFIRMADA"));

        mvc.perform(get("/api/notas-entrada/{id}/detalhes", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordemCompra.id").value(ordemId))
                .andExpect(jsonPath("$.ordemCompra.numero").value("OC-FISCAL"))
                .andExpect(jsonPath("$.recebimento.id").value(recebimentoId))
                .andExpect(jsonPath("$.recebimento.itens[0].produtoId").value(produtoA))
                .andExpect(jsonPath("$.contasPagar.length()").value(1))
                .andExpect(jsonPath("$.movimentosEstoque.length()").value(1))
                .andExpect(jsonPath("$.movimentosEstoque[0].origemTipo")
                        .value("ORDEM_COMPRA"));

        assertThat(jdbc.queryForObject("""
                SELECT quantidade FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, BigDecimal.class, tenantA.organizacaoId(), produtoA, localA))
                .isEqualByComparingTo("5");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM movimento_estoque
                WHERE organizacao_id=? AND produto_id=?
                """, Integer.class, tenantA.organizacaoId(), produtoA)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM conta_pagar
                WHERE organizacao_id=? AND nota_entrada_id=?
                """, Integer.class, tenantA.organizacaoId(), notaId)).isEqualTo(1);
    }

    @Test
    void editaRascunhoReutilizandoItemEProtegeVersao() throws Exception {
        String criada = mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-edicao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-EDICAO", fornecedorA, produtoA, "1")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ordemId = JsonPath.<Number>read(criada, "$.id").longValue();
        long itemId = JsonPath.<Number>read(criada, "$.itens[0].id").longValue();

        mvc.perform(put("/api/v1/purchase-orders/{id}", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"9\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-EDICAO", fornecedorA, produtoA, "3")))
                .andExpect(status().isPreconditionFailed());

        mvc.perform(put("/api/v1/purchase-orders/{id}", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-EDICAO", fornecedorA, produtoA, "3")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.itens[0].id").value(itemId))
                .andExpect(jsonPath("$.itens[0].quantidadePedida").value(3));
    }

    private org.springframework.test.web.servlet.ResultActions receber(
            long ordemId, long itemId, String quantidade, String etag, String chave)
            throws Exception {
        return mvc.perform(post("/api/v1/purchase-orders/{id}/receipts", ordemId)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header(HttpHeaders.IF_MATCH, etag)
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"localEstoqueId\":" + localA + ",\"itens\":[{"
                        + "\"itemOrdemCompraId\":" + itemId + ",\"quantidade\":"
                        + quantidade + "}]}"));
    }

    private Long criarFornecedor(Long org, String sufixo) {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setOrganizacaoId(org);
        fornecedor.setNome("Fornecedor compra " + sufixo);
        fornecedor.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        return fornecedores.saveAndFlush(fornecedor).getId();
    }

    private Long criarProduto(Long org, String sufixo) {
        Marca marca = new Marca();
        marca.setOrganizacaoId(org);
        marca.setNome("Marca compra " + sufixo);
        marca = marcas.save(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(org);
        unidade.setNome("Unidade compra " + sufixo);
        unidade.setSigla("OC" + sufixo);
        unidade = unidades.save(unidade);
        Produto produto = new Produto();
        produto.setOrganizacaoId(org);
        produto.setNome("Produto compra " + sufixo);
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

    private String ordem(String numero, Long fornecedor, Long produto, String quantidade) {
        return "{\"numero\":\"" + numero + "\",\"fornecedorId\":" + fornecedor
                + ",\"moeda\":\"BRL\",\"itens\":[{\"produtoId\":" + produto
                + ",\"quantidade\":" + quantidade + ",\"valorUnitario\":10}]}";
    }
}
