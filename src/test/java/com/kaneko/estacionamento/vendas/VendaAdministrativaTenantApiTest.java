package com.kaneko.estacionamento.vendas;

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
class VendaAdministrativaTenantApiTest {

    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired ClienteRepository clientes;
    @Autowired MarcaRepository marcas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ProdutoRepository produtos;
    @Autowired JdbcTemplate jdbc;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long clienteA;
    private Long clienteB;
    private Long produtoA;
    private Long localA;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        clienteA = criarCliente(tenantA.organizacaoId(), "A");
        clienteB = criarCliente(tenantB.organizacaoId(), "B");
        produtoA = criarProduto(tenantA.organizacaoId(), "A");
        localA = criarLocal(tenantA, "Expedicao venda");
        ajustar("saldo-venda", "10");
    }

    @Test
    void criaEConfirmaVendaBaixandoEstoqueEGerandoRecebivel() throws Exception {
        String criada = mvc.perform(post("/api/v1/administrative-sales")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "venda-criar-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venda("VENDA-001", clienteA, produtoA, "3")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andReturn().getResponse().getContentAsString();
        long vendaId = JsonPath.<Number>read(criada, "$.id").longValue();

        mvc.perform(post("/api/v1/administrative-sales/{id}/confirmation", vendaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/v1/administrative-sales/{id}/confirmation", vendaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));

        assertThat(jdbc.queryForObject("""
                SELECT quantidade FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, BigDecimal.class, tenantA.organizacaoId(), produtoA, localA))
                .isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM movimento_estoque
                WHERE organizacao_id=? AND origem_tipo='VENDA_ADMINISTRATIVA'
                """, Integer.class, tenantA.organizacaoId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM conta_receber
                WHERE organizacao_id=? AND venda_administrativa_id=?
                """, Integer.class, tenantA.organizacaoId(), vendaId)).isEqualTo(1);
    }

    @Test
    void notaVinculadaDocumentaVendaSemDuplicarEstoqueOuRecebivel() throws Exception {
        String criada = mvc.perform(post("/api/v1/administrative-sales")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "venda-fiscal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venda("VENDA-FISCAL", clienteA, produtoA, "2")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long vendaId = JsonPath.<Number>read(criada, "$.id").longValue();
        mvc.perform(post("/api/v1/administrative-sales/{id}/confirmation", vendaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk());

        String nota = mvc.perform(post("/api/notas-saida")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"NF-VENDA-1\",\"clienteId\":" + clienteA
                                + ",\"localEstoqueId\":" + localA
                                + ",\"vendaAdministrativaId\":" + vendaId
                                + ",\"itens\":[{\"produtoId\":" + produtoA
                                + ",\"quantidade\":2,\"valorUnitario\":10}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendaAdministrativaId").value(vendaId))
                .andReturn().getResponse().getContentAsString();
        long notaId = JsonPath.<Number>read(nota, "$.id").longValue();
        mvc.perform(post("/api/notas-saida/{id}/confirmacao", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CONFIRMADA"));

        assertThat(jdbc.queryForObject("""
                SELECT quantidade FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, BigDecimal.class, tenantA.organizacaoId(), produtoA, localA))
                .isEqualByComparingTo("8");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM movimento_estoque
                WHERE organizacao_id=? AND origem_tipo='VENDA_ADMINISTRATIVA'
                """, Integer.class, tenantA.organizacaoId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM conta_receber
                WHERE organizacao_id=? AND venda_administrativa_id=?
                """, Integer.class, tenantA.organizacaoId(), vendaId)).isEqualTo(1);
    }

    @Test
    void isolaTenantERecusaClienteDeOutraOrganizacao() throws Exception {
        mvc.perform(post("/api/v1/administrative-sales")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "venda-cross")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venda("VENDA-CROSS", clienteB, produtoA, "1")))
                .andExpect(status().isNotFound());

        String criada = mvc.perform(post("/api/v1/administrative-sales")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "venda-isolada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venda("VENDA-ISOLADA", clienteA, produtoA, "1")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long vendaId = JsonPath.<Number>read(criada, "$.id").longValue();
        mvc.perform(get("/api/v1/administrative-sales/{id}", vendaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/administrative-sales")
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void protegeEdicaoPorVersaoEReverteConfirmacaoSemEstoque() throws Exception {
        String criada = mvc.perform(post("/api/v1/administrative-sales")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "venda-versao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venda("VENDA-VERSAO", clienteA, produtoA, "11")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long vendaId = JsonPath.<Number>read(criada, "$.id").longValue();
        long itemId = JsonPath.<Number>read(criada, "$.itens[0].id").longValue();

        mvc.perform(put("/api/v1/administrative-sales/{id}", vendaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"9\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venda("VENDA-VERSAO", clienteA, produtoA, "11")))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(put("/api/v1/administrative-sales/{id}", vendaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venda("VENDA-VERSAO", clienteA, produtoA, "11")))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.itens[0].id").value(itemId));
        assertThat(jdbc.queryForObject("""
                SELECT quantidade FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, BigDecimal.class, tenantA.organizacaoId(), produtoA, localA))
                .isEqualByComparingTo("10");
        mvc.perform(post("/api/v1/administrative-sales/{id}/confirmation", vendaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/administrative-sales/{id}", vendaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RASCUNHO"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM conta_receber
                WHERE organizacao_id=? AND venda_administrativa_id=?
                """, Integer.class, tenantA.organizacaoId(), vendaId)).isZero();
    }

    @Test
    void geraNotaPeloPedidoEConsultaDocumentosSemRedigitarOuDuplicar() throws Exception {
        String criada = mvc.perform(post("/api/v1/administrative-sales")
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header("Idempotency-Key", "fluxo-venda")
                .contentType(MediaType.APPLICATION_JSON)
                .content(venda("FLUXO-VENDA", clienteA, produtoA, "2")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Number>read(criada, "$.id").longValue();
        String rota = "/api/v1/administrative-sales/" + id + "/outbound-note";
        mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content("{\"numero\":\"NF-FLUXO\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header("Idempotency-Key", " ").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"NF-FLUXO\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header("Idempotency-Key", "nf-pedido").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"NF-FLUXO\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/administrative-sales/{id}/confirmation", id)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header(HttpHeaders.IF_MATCH, "\"0\"")).andExpect(status().isOk());
        String nota = mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header("Idempotency-Key", "nf-pedido").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"NF-FLUXO\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clienteId").value(clienteA))
                .andExpect(jsonPath("$.itens[0].produtoId").value(produtoA))
                .andExpect(jsonPath("$.valorTotal").value(20))
                .andReturn().getResponse().getContentAsString();
        long notaId = JsonPath.<Number>read(nota, "$.id").longValue();
        mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header("Idempotency-Key", "nf-pedido").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"NF-FLUXO\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(notaId));
        mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                .header("Idempotency-Key", "nf-pedido").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"OUTRA\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/administrative-sales/{id}/documents", id)
                .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nota.id").value(notaId))
                .andExpect(jsonPath("$.contas.length()").value(1))
                .andExpect(jsonPath("$.movimentos.length()").value(1));
        mvc.perform(get("/api/v1/administrative-sales/{id}/documents", id)
                .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual())
                .header("Idempotency-Key", "nf-outro-tenant").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"NF-FLUXO\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM nota_saida WHERE venda_administrativa_id=?",
                Integer.class, id)).isEqualTo(1);
    }

    private Long criarCliente(Long org, String sufixo) {
        Cliente cliente = new Cliente();
        cliente.setOrganizacaoId(org);
        cliente.setNome("Cliente venda " + sufixo);
        cliente.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        return clientes.saveAndFlush(cliente).getId();
    }

    private Long criarProduto(Long org, String sufixo) {
        Marca marca = new Marca();
        marca.setOrganizacaoId(org);
        marca.setNome("Marca venda " + sufixo);
        marca = marcas.save(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(org);
        unidade.setNome("Unidade venda " + sufixo);
        unidade.setSigla("VA" + sufixo);
        unidade = unidades.save(unidade);
        Produto produto = new Produto();
        produto.setOrganizacaoId(org);
        produto.setNome("Produto venda " + sufixo);
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

    private void ajustar(String chave, String delta) throws Exception {
        mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":" + produtoA + ",\"localEstoqueId\":"
                                + localA + ",\"delta\":" + delta
                                + ",\"motivo\":\"Saldo inicial da venda\"}"))
                .andExpect(status().isCreated());
    }

    private String venda(String numero, Long cliente, Long produto, String quantidade) {
        return "{\"numero\":\"" + numero + "\",\"clienteId\":" + cliente
                + ",\"localEstoqueId\":" + localA
                + ",\"moeda\":\"BRL\",\"itens\":[{\"produtoId\":" + produto
                + ",\"quantidade\":" + quantidade + ",\"valorUnitario\":10}]}";
    }
}
