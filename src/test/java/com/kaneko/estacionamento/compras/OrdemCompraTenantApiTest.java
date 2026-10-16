package com.kaneko.estacionamento.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.kaneko.estacionamento.conveniencia.ProdutoFornecedor;
import com.kaneko.estacionamento.conveniencia.ProdutoFornecedorRepository;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.conveniencia.UnidadeMedida;
import com.kaneko.estacionamento.conveniencia.UnidadeMedidaRepository;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.logistica.Transportadora;
import com.kaneko.estacionamento.logistica.TransportadoraRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    @Autowired ProdutoFornecedorRepository produtoFornecedores;
    @Autowired CondicaoPagamentoRepository condicoesPagamento;
    @Autowired TransportadoraRepository transportadoras;
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
    void documentosExigemPermissaoDeCompraMesmoComMembershipAtiva() throws Exception {
        String login = "semcompra" + UUID.randomUUID().toString().substring(0, 8);
        var user = tenant.criarUsuario(login, com.kaneko.estacionamento.usuario.PerfilUsuario.USUARIO);
        tenant.criarMembership(tenantA.organizacaoId(), user.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/v1/purchase-orders/1/documents")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void criaAprovaEExigeRecebimentoIntegral() throws Exception {
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

        receber(ordemId, itemId, "2", "\"1\"", "receber-parcial")
                .andExpect(status().isConflict());

        String recebimento = receber(ordemId, itemId, "5", "\"1\"", "receber-1")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.itens[0].quantidade").value(5))
                .andReturn().getResponse().getContentAsString();
        long recebimentoId = JsonPath.<Number>read(recebimento, "$.id").longValue();

        receber(ordemId, itemId, "5", "\"1\"", "receber-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(recebimentoId));
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
                """, Integer.class, tenantA.organizacaoId())).isEqualTo(1);
    }

    @Test
    void rejeitaChaveFiscalDuplicadaNoMesmoFornecedor() throws Exception {
        mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-chave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-CHAVE-1", fornecedorA, produtoA, "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroNota").value("OC-CHAVE-1"))
                .andExpect(jsonPath("$.serieNota").value("1"))
                .andExpect(jsonPath("$.modeloNota").value("55"));

        mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-chave-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-CHAVE-1", fornecedorA, produtoA, "1")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejeitaEntregaIntegralSemMovimentarEstoque() throws Exception {
        String criada = mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-rejeicao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("NF-REJEITADA", fornecedorA, produtoA, "2")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ordemId = JsonPath.<Number>read(criada, "$.id").longValue();

        mvc.perform(post("/api/v1/purchase-orders/{id}/approval", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/purchase-orders/{id}/receipt-rejection", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Volumes divergentes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADA"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimento_estoque WHERE origem_tipo='ORDEM_COMPRA' AND origem_chave LIKE ?",
                Integer.class, ordemId + ":%" )).isZero();
    }

    @Test
    void calculaDespesasRateadasECustoFinalDosItens() throws Exception {
        Long segundoProduto = criarProduto(tenantA.organizacaoId(), "R");
        ProdutoFornecedor referencia = new ProdutoFornecedor();
        referencia.setOrganizacaoId(tenantA.organizacaoId());
        referencia.setProduto(produtos.getReferenceById(produtoA));
        referencia.setFornecedor(fornecedores.getReferenceById(fornecedorA));
        referencia.setCodigoProd("FORN-001");
        produtoFornecedores.saveAndFlush(referencia);
        String payload = """
                {"numeroNota":"OC-RATEIO","serieNota":"1","modeloNota":"55","fornecedorId":%d,"moeda":"BRL",
                 "valorFrete":29,"valorSeguro":14.50,"outrasDespesas":5.80,
                 "valorDesconto":0,"itens":[
                   {"produtoId":%d,"quantidade":2,"valorUnitario":100,"valorDesconto":10},
                   {"produtoId":%d,"quantidade":1,"valorUnitario":100,"valorDesconto":0}
                 ]}
                """.formatted(fornecedorA, produtoA, segundoProduto);

        String criada = mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-rateio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(290.00))
                .andExpect(jsonPath("$.valorFrete").value(29.00))
                .andExpect(jsonPath("$.valorSeguro").value(14.50))
                .andExpect(jsonPath("$.outrasDespesas").value(5.80))
                .andExpect(jsonPath("$.valorDesconto").value(0.00))
                .andExpect(jsonPath("$.valorTotal").value(339.30))
                .andExpect(jsonPath("$.itens[0].valorTotal").value(190.00))
                .andExpect(jsonPath("$.itens[0].codigoProdutoFornecedor").value("FORN-001"))
                .andExpect(jsonPath("$.itens[0].rateioFrete").value(19.00))
                .andExpect(jsonPath("$.itens[0].rateioSeguro").value(9.50))
                .andExpect(jsonPath("$.itens[0].rateioOutrasDespesas").value(3.80))
                .andExpect(jsonPath("$.itens[0].custoTotal").value(222.30))
                .andExpect(jsonPath("$.itens[0].custoUnitarioFinal").value(111.15))
                .andExpect(jsonPath("$.itens[1].rateioFrete").value(10.00))
                .andExpect(jsonPath("$.itens[1].rateioSeguro").value(5.00))
                .andExpect(jsonPath("$.itens[1].rateioOutrasDespesas").value(2.00))
                .andExpect(jsonPath("$.itens[1].custoTotal").value(117.00))
                .andExpect(jsonPath("$.itens[1].custoUnitarioFinal").value(117.00))
                .andReturn().getResponse().getContentAsString();

        long ordemId = JsonPath.<Number>read(criada, "$.id").longValue();
        long primeiroItem = JsonPath.<Number>read(criada, "$.itens[0].id").longValue();
        long segundoItem = JsonPath.<Number>read(criada, "$.itens[1].id").longValue();
        mvc.perform(post("/api/v1/purchase-orders/{id}/approval", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/purchase-orders/{id}/receipts", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .header("Idempotency-Key", "oc-rateio-receber")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localEstoqueId\":" + localA + ",\"itens\":["
                                + "{\"itemOrdemCompraId\":" + primeiroItem + ",\"quantidade\":2},"
                                + "{\"itemOrdemCompraId\":" + segundoItem + ",\"quantidade\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens[0].custoUnitario").value(111.15))
                .andExpect(jsonPath("$.itens[1].custoUnitario").value(117.00));
    }

    @Test
    void rejeitaDescontoGeralDaOrdem() throws Exception {
        String payload = """
                {"numeroNota":"OC-DESCONTO-GERAL","serieNota":"1","modeloNota":"55","fornecedorId":%d,"moeda":"BRL",
                 "valorDesconto":1,"itens":[
                   {"produtoId":%d,"quantidade":1,"valorUnitario":10,"valorDesconto":0}
                 ]}
                """.formatted(fornecedorA, produtoA);

        mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-desconto-geral")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.valorDesconto").exists());
    }

    @Test
    void rejeitaDescontoDoItemSuperiorAoValorBruto() throws Exception {
        String payload = """
                {"numeroNota":"OC-DESCONTO-ITEM","serieNota":"1","modeloNota":"55","fornecedorId":%d,"moeda":"BRL",
                 "valorDesconto":0,"itens":[
                   {"produtoId":%d,"quantidade":11,"valorUnitario":12,"valorDesconto":1111}
                 ]}
                """.formatted(fornecedorA, produtoA);

        mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-desconto-item-excessivo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("O desconto do item nao pode exceder seu valor."));
    }

    @Test
    void herdaCondicaoETransportadoraDoFornecedorERegistraDadosComerciais() throws Exception {
        CondicaoPagamento condicao = new CondicaoPagamento();
        condicao.setOrganizacaoId(tenantA.organizacaoId());
        condicao.setNome("30 dias");
        condicao = condicoesPagamento.saveAndFlush(condicao);

        Transportadora transportadora = new Transportadora();
        transportadora.setOrganizacaoId(tenantA.organizacaoId());
        transportadora.setNome("Transportadora da compra");
        transportadora.setDocumento(UUID.randomUUID().toString().replace("-", "")
                .substring(0, 14));
        transportadora = transportadoras.saveAndFlush(transportadora);

        Fornecedor fornecedor = fornecedores.findByIdAndOrganizacaoId(
                fornecedorA, tenantA.organizacaoId()).orElseThrow();
        fornecedor.setCondicaoPagamento(condicao);
        fornecedor.setTransportadora(transportadora);
        fornecedores.saveAndFlush(fornecedor);

        String payload = """
                {"numeroNota":"OC-COMERCIAL","serieNota":"1","modeloNota":"55","fornecedorId":%d,"moeda":"BRL",
                 "localEstoqueEntregaId":%d,"tipoFrete":"FOB",
                 "referenciaFornecedor":"PED-7788",
                 "observacao":"Entregar na doca 1",
                 "observacaoInterna":"Conferir lote e validade",
                 "itens":[{"produtoId":%d,"quantidade":1,"valorUnitario":10}]}
                """.formatted(fornecedorA, localA, produtoA);

        mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-comercial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.condicaoPagamentoId").value(condicao.getId()))
                .andExpect(jsonPath("$.condicaoPagamentoNome").value("30 dias"))
                .andExpect(jsonPath("$.transportadoraId").value(transportadora.getId()))
                .andExpect(jsonPath("$.transportadoraNome")
                        .value("Transportadora da compra"))
                .andExpect(jsonPath("$.localEstoqueEntregaId").value(localA))
                .andExpect(jsonPath("$.localEstoqueEntregaNome")
                        .value("Recebimento compras"))
                .andExpect(jsonPath("$.tipoFrete").value("FOB"))
                .andExpect(jsonPath("$.referenciaFornecedor").value("PED-7788"))
                .andExpect(jsonPath("$.observacaoInterna")
                        .value("Conferir lote e validade"))
                .andExpect(jsonPath("$.compradorId").value(tenantA.usuarioId()))
                .andExpect(jsonPath("$.compradorNome").isNotEmpty());
    }

    @Test
    void rejeitaReferenciasComerciaisDeOutroTenant() throws Exception {
        CondicaoPagamento condicaoOutroTenant = new CondicaoPagamento();
        condicaoOutroTenant.setOrganizacaoId(tenantB.organizacaoId());
        condicaoOutroTenant.setNome("Condicao de outro tenant");
        condicaoOutroTenant = condicoesPagamento.saveAndFlush(condicaoOutroTenant);

        String payload = """
                {"numeroNota":"OC-CROSS-REF","serieNota":"1","modeloNota":"55","fornecedorId":%d,"moeda":"BRL",
                 "condicaoPagamentoId":%d,
                 "itens":[{"produtoId":%d,"quantidade":1,"valorUnitario":10}]}
                """.formatted(fornecedorA, condicaoOutroTenant.getId(), produtoA);

        mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-cross-ref")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaDataDaOrdemNoFuturo() throws Exception {
        String payload = """
                {"numeroNota":"OC-FUTURA","serieNota":"1","modeloNota":"55","fornecedorId":%d,"moeda":"BRL",
                 "dataEmissao":"%s",
                 "itens":[{"produtoId":%d,"quantidade":1,"valorUnitario":10}]}
                """.formatted(fornecedorA, LocalDate.now().plusDays(1), produtoA);

        mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-futura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void bancoImpedeDescontoGeralMesmoForaDoService() throws Exception {
        String criada = mvc.perform(post("/api/v1/purchase-orders")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "oc-constraint-desconto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordem("OC-CONSTRAINT", fornecedorA, produtoA, "1")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ordemId = JsonPath.<Number>read(criada, "$.id").longValue();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE ordem_compra SET valor_desconto=1 WHERE id=?", ordemId))
                .hasMessageContaining("ck_ordem_compra_valores");
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
        mvc.perform(get("/api/v1/purchase-orders").param("numero", "ISOLADA").param("status", "APROVADA")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/purchase-orders").param("numero", "isolada").param("status", "RASCUNHO")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
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
        long movimentoId = JsonPath.<Number>read(recebida, "$.itens[0].movimentoEstoqueId").longValue();
        mvc.perform(get("/api/v1/stock-movements/{id}/origin", movimentoId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("RECEBIMENTO_COMPRA"))
                .andExpect(jsonPath("$.id").value(recebimentoId))
                .andExpect(jsonPath("$.caminho").value("/app/ordens-compra?detail=" + ordemId
                        + "&recebimentoId=" + recebimentoId));
        mvc.perform(get("/api/v1/stock-movements/{id}/origin", movimentoId)
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isNotFound());

        String nota = mvc.perform(post("/api/notas-entrada")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"OC-FISCAL\",\"fornecedorId\":" + fornecedorA
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

        mvc.perform(post("/api/notas-entrada/{id}/confirmacao", notaId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/purchase-orders/{id}/documents", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recebimentos[0].id").value(recebimentoId))
                .andExpect(jsonPath("$.recebimentos[0].atorNome").isNotEmpty())
                .andExpect(jsonPath("$.notas[0].id").value(notaId))
                .andExpect(jsonPath("$.contasPagar.length()").value(1))
                .andExpect(jsonPath("$.movimentosEstoque.length()").value(1));
        mvc.perform(get("/api/v1/purchase-orders/{id}/documents", ordemId)
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isNotFound());

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
        return "{\"numeroNota\":\"" + numero
                + "\",\"serieNota\":\"1\",\"modeloNota\":\"55\",\"fornecedorId\":" + fornecedor
                + ",\"moeda\":\"BRL\",\"itens\":[{\"produtoId\":" + produto
                + ",\"quantidade\":" + quantidade + ",\"valorUnitario\":10}]}";
    }
}
