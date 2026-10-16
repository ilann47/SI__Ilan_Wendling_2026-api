package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.conveniencia.*;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.support.tenant.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class ImportacaoNotaEntradaTenantApiTest {
    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired JdbcTemplate jdbc;
    @Autowired FornecedorRepository fornecedores;
    @Autowired ProdutoRepository produtos;
    @Autowired MarcaRepository marcas;
    @Autowired UnidadeMedidaRepository unidades;

    @Test void importaConciliaConfereELancaSemDuplicar() throws Exception {
        var t = tenant.criarTenantAdmin();
        String documento = jdbc.queryForObject("select documento from organizacao where id=?", String.class, t.organizacaoId());
        String xml = NotaEntradaXmlParserTest.xml(documento);
        String importada = mvc.perform(post("/api/notas-entrada/importacoes").header("Authorization", t.tokenContextual())
                .contentType(MediaType.APPLICATION_XML).content(xml)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IMPORTADA")).andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Number>read(importada, "$.id").longValue();
        mvc.perform(get("/api/notas-entrada/importacoes?page=0&size=200").header("Authorization",t.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(50)).andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].xmlOriginal").doesNotExist());
        mvc.perform(post("/api/notas-entrada/importacoes").header("Authorization", t.tokenContextual())
                .contentType(MediaType.APPLICATION_XML).content(xml)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));
        assertThat(jdbc.queryForObject("select count(*) from nota_entrada where organizacao_id=?", Integer.class, t.organizacaoId())).isZero();
        var outro = tenant.criarTenantAdmin();
        mvc.perform(get("/api/notas-entrada/importacoes/{id}", id).header("Authorization", outro.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/notas-entrada/importacoes").header("Authorization", outro.tokenContextual())
                .contentType(MediaType.APPLICATION_XML).content(xml)).andExpect(status().isConflict());
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setOrganizacaoId(t.organizacaoId()); fornecedor.setNome("Fornecedor XML"); fornecedor.setDocumento("11222333000181");
        long fornecedorId = fornecedores.saveAndFlush(fornecedor).getId();
        Marca marca = new Marca(); marca.setNome("XML"); marca.setOrganizacaoId(t.organizacaoId()); marcas.saveAndFlush(marca);
        UnidadeMedida un = new UnidadeMedida(); un.setNome("Unidade"); un.setSigla("UN"); un.setOrganizacaoId(t.organizacaoId()); unidades.saveAndFlush(un);
        Produto produto = new Produto(); produto.setOrganizacaoId(t.organizacaoId()); produto.setNome("Produto XML"); produto.setMarca(marca); produto.setUnidadeMedida(un);
        long produtoId = produtos.saveAndFlush(produto).getId();
        String local = mvc.perform(post("/api/v1/stock-locations").header("Authorization", t.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Depósito XML\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long localId = JsonPath.<Number>read(local, "$.id").longValue();
        String base = "/api/notas-entrada/importacoes/" + id;
        String conciliar = "{\"fornecedorId\":" + fornecedorId + ",\"localEstoqueId\":" + localId
                + ",\"itens\":[{\"sequencia\":1,\"produtoId\":" + produtoId + ",\"fatorConversao\":1,\"valorUnitario\":10}]}";
        mvc.perform(put(base + "/conciliacao").header("Authorization", t.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content(conciliar)).andExpect(status().isPreconditionRequired());
        mvc.perform(put(base + "/conciliacao").header("Authorization", t.tokenContextual()).header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(conciliar)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCILIADA"));
        mvc.perform(post(base + "/conferencia").header("Authorization", t.tokenContextual()).header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"sequencia\":1,\"quantidadeConferida\":1}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCILIADA"));
        mvc.perform(post(base + "/confirmacao").header("Authorization", t.tokenContextual()).header("If-Match", "\"2\""))
                .andExpect(status().isConflict());
        mvc.perform(post(base + "/conferencia").header("Authorization", t.tokenContextual()).header("If-Match", "\"2\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"sequencia\":1,\"quantidadeConferida\":2}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFERIDA"));
        mvc.perform(post(base + "/confirmacao").header("Authorization", t.tokenContextual()).header("If-Match", "\"3\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.notaEntradaId").isNumber());
        mvc.perform(post(base + "/confirmacao").header("Authorization", t.tokenContextual()).header("If-Match", "\"3\""))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select quantidade from saldo_estoque where organizacao_id=? and produto_id=? and local_estoque_id=?",
                BigDecimal.class, t.organizacaoId(), produtoId, localId)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("select count(*) from conta_pagar where organizacao_id=?", Integer.class, t.organizacaoId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from nota_entrada where organizacao_id=?", Integer.class, t.organizacaoId())).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void xmlComCompraReutilizaOuCriaRecebimentoEConfirmaConcorrentemente(boolean jaRecebido) throws Exception {
        var ctx = contexto(); var t = ctx.tenant();
        String compra = mvc.perform(post("/api/v1/purchase-orders").header("Authorization",t.tokenContextual())
                .header("Idempotency-Key","oc-xml").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numeroNota\":\"123\",\"serieNota\":\"1\",\"modeloNota\":\"55\",\"fornecedorId\":"+ctx.fornecedorId()+",\"moeda\":\"BRL\",\"itens\":[{\"produtoId\":"
                        +ctx.produtoId()+",\"quantidade\":2,\"valorUnitario\":10}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ordemId=JsonPath.<Number>read(compra,"$.id").longValue();
        long itemId=JsonPath.<Number>read(compra,"$.itens[0].id").longValue();
        mvc.perform(post("/api/v1/purchase-orders/{id}/approval",ordemId).header("Authorization",t.tokenContextual())
                .header("If-Match","\"0\"")).andExpect(status().isOk());
        Long recebidoId=null;
        if (jaRecebido) {
            String recebido=mvc.perform(post("/api/v1/purchase-orders/{id}/receipts",ordemId)
                    .header("Authorization",t.tokenContextual()).header("Idempotency-Key","rc-xml")
                    .header("If-Match","\"1\"").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"localEstoqueId\":"+ctx.localId()+",\"itens\":[{\"itemOrdemCompraId\":"+itemId+",\"quantidade\":2}]}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            recebidoId=JsonPath.<Number>read(recebido,"$.id").longValue();
        }
        try (var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var largada=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> importar=() -> {
                largada.await();
                String json=mvc.perform(post("/api/notas-entrada/importacoes").header("Authorization",t.tokenContextual())
                        .contentType(MediaType.APPLICATION_XML).content(ctx.xml())).andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString();
                return JsonPath.<Number>read(json,"$.id").longValue();
            };
            var a=pool.submit(importar); var b=pool.submit(importar); largada.countDown();
            long id=a.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(b.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(id);
            String rota="/api/notas-entrada/importacoes/"+id;
            String reconciliacao="{\"fornecedorId\":"+ctx.fornecedorId()+",\"localEstoqueId\":"+ctx.localId()
                    +",\"ordemCompraId\":"+ordemId+",\"recebimentoCompraId\":"+recebidoId+",\"registrarNovoRecebimento\":"+!jaRecebido
                    +",\"itens\":[{\"sequencia\":1,\"produtoId\":"+ctx.produtoId()+",\"fatorConversao\":1,\"valorUnitario\":10,\"itemOrdemCompraId\":"+itemId+"}]}";
            mvc.perform(put(rota+"/conciliacao").header("Authorization",t.tokenContextual()).header("If-Match","\"0\"")
                    .contentType(MediaType.APPLICATION_JSON).content(reconciliacao)).andExpect(status().isOk());
            mvc.perform(post(rota+"/conferencia").header("Authorization",t.tokenContextual()).header("If-Match","\"1\"")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"sequencia\":1,\"quantidadeConferida\":2}]}"))
                    .andExpect(status().isOk());
            var confirmarInicio=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> confirmar=() -> {
                confirmarInicio.await();
                String json=mvc.perform(post(rota+"/confirmacao").header("Authorization",t.tokenContextual())
                        .header("If-Match","\"2\"")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                return JsonPath.<Number>read(json,"$.notaEntradaId").longValue();
            };
            var c=pool.submit(confirmar); var d=pool.submit(confirmar); confirmarInicio.countDown();
            assertThat(c.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(d.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("select count(*) from recebimento_compra where ordem_compra_id=?",Integer.class,ordemId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select quantidade from saldo_estoque where organizacao_id=? and produto_id=? and local_estoque_id=?",
                    BigDecimal.class,t.organizacaoId(),ctx.produtoId(),ctx.localId())).isEqualByComparingTo("2");
            assertThat(jdbc.queryForObject("select count(*) from conta_pagar where organizacao_id=?",Integer.class,t.organizacaoId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select quantidade_recebida from item_ordem_compra where id=?",BigDecimal.class,itemId)).isEqualByComparingTo("2");
        }
    }

    @Test void naoConfirmaTotalFiscalNaoRepresentavelENaoVazaProdutoDeOutroTenant() throws Exception {
        var ctx=contexto(); var outro=contexto();
        String xml=ctx.xml().replace("<vNF>20.00</vNF>","<vNF>22.00</vNF>");
        String json=mvc.perform(post("/api/notas-entrada/importacoes").header("Authorization",ctx.tenant().tokenContextual())
                .contentType(MediaType.APPLICATION_XML).content(xml)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.divergencias.length()").value(1)).andReturn().getResponse().getContentAsString();
        long id=JsonPath.<Number>read(json,"$.id").longValue();
        String rota="/api/notas-entrada/importacoes/"+id;
        String body="{\"fornecedorId\":"+ctx.fornecedorId()+",\"localEstoqueId\":"+ctx.localId()
                +",\"itens\":[{\"sequencia\":1,\"produtoId\":%s,\"fatorConversao\":1,\"valorUnitario\":10}]}";
        mvc.perform(put(rota+"/conciliacao").header("Authorization",ctx.tenant().tokenContextual()).header("If-Match","\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(outro.produtoId()))).andExpect(status().isNotFound());
        mvc.perform(put(rota+"/conciliacao").header("Authorization",ctx.tenant().tokenContextual()).header("If-Match","\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(ctx.produtoId()))).andExpect(status().isOk());
        mvc.perform(post(rota+"/conferencia").header("Authorization",ctx.tenant().tokenContextual()).header("If-Match","\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"sequencia\":1,\"quantidadeConferida\":2}]}"))
                .andExpect(status().isOk());
        mvc.perform(post(rota+"/confirmacao").header("Authorization",ctx.tenant().tokenContextual()).header("If-Match","\"2\""))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from nota_entrada where organizacao_id=?",Integer.class,ctx.tenant().organizacaoId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from movimento_estoque where organizacao_id=?",Integer.class,ctx.tenant().organizacaoId())).isZero();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void desfazRecebimentoInteiroSeNotaDuplicadaFalharNaMesmaTransacao() throws Exception {
        var ctx=contexto(); var t=ctx.tenant();
        mvc.perform(post("/api/notas-entrada").header("Authorization",t.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content("{\"numero\":\"123\",\"serie\":\"1\",\"fornecedorId\":"
                        +ctx.fornecedorId()+",\"localEstoqueId\":"+ctx.localId()+",\"dataEmissao\":\"2026-09-12\",\"itens\":[{\"produtoId\":"
                        +ctx.produtoId()+",\"quantidade\":2,\"valorUnitario\":10}]}"))
                .andExpect(status().isCreated());
        String compra=mvc.perform(post("/api/v1/purchase-orders").header("Authorization",t.tokenContextual())
                .header("Idempotency-Key","rollback-xml").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numeroNota\":\"123\",\"serieNota\":\"1\",\"modeloNota\":\"55\",\"fornecedorId\":"+ctx.fornecedorId()+",\"moeda\":\"BRL\",\"itens\":[{\"produtoId\":"
                        +ctx.produtoId()+",\"quantidade\":2,\"valorUnitario\":10}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ordemId=JsonPath.<Number>read(compra,"$.id").longValue();
        long itemId=JsonPath.<Number>read(compra,"$.itens[0].id").longValue();
        mvc.perform(post("/api/v1/purchase-orders/{id}/approval",ordemId).header("Authorization",t.tokenContextual())
                .header("If-Match","\"0\"")).andExpect(status().isOk());
        String importada=mvc.perform(post("/api/notas-entrada/importacoes").header("Authorization",t.tokenContextual())
                .contentType(MediaType.APPLICATION_XML).content(ctx.xml())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id=JsonPath.<Number>read(importada,"$.id").longValue();
        String rota="/api/notas-entrada/importacoes/"+id;
        mvc.perform(put(rota+"/conciliacao").header("Authorization",t.tokenContextual()).header("If-Match","\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"fornecedorId\":"+ctx.fornecedorId()+",\"localEstoqueId\":"+ctx.localId()
                        +",\"ordemCompraId\":"+ordemId+",\"registrarNovoRecebimento\":true,\"itens\":[{\"sequencia\":1,\"produtoId\":"+ctx.produtoId()
                        +",\"fatorConversao\":1,\"valorUnitario\":10,\"itemOrdemCompraId\":"+itemId+"}]}"))
                .andExpect(status().isOk());
        mvc.perform(post(rota+"/conferencia").header("Authorization",t.tokenContextual()).header("If-Match","\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"sequencia\":1,\"quantidadeConferida\":2}]}"))
                .andExpect(status().isOk());
        mvc.perform(post(rota+"/confirmacao").header("Authorization",t.tokenContextual()).header("If-Match","\"2\""))
                .andExpect(status().isConflict());
        mvc.perform(get(rota).header("Authorization",t.tokenContextual())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFERIDA")).andExpect(jsonPath("$.notaEntradaId").isEmpty())
                .andExpect(jsonPath("$.recebimentoCompraId").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from recebimento_compra where ordem_compra_id=?",Integer.class,ordemId)).isZero();
        assertThat(jdbc.queryForObject("select quantidade_recebida from item_ordem_compra where id=?",BigDecimal.class,itemId)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select count(*) from movimento_estoque where organizacao_id=?",Integer.class,t.organizacaoId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from conta_pagar where organizacao_id=?",Integer.class,t.organizacaoId())).isZero();
    }

    @Test void exigePermissaoFiscalMesmoEmChamadaDireta() throws Exception {
        var ctx=contexto(); var t=ctx.tenant();
        var usuario=tenant.criarUsuario("xml_operador_"+t.organizacaoId(),com.kaneko.estacionamento.usuario.PerfilUsuario.OPERADOR);
        tenant.criarMembership(t.organizacaoId(),usuario.getId(),t.tokenContextual());
        String operador=tenant.selecionarOrganizacao(t.organizacaoId(),tenant.autenticar(usuario.getLogin(),"senha123"));
        mvc.perform(post("/api/notas-entrada/importacoes").header("Authorization",operador)
                .contentType(MediaType.APPLICATION_XML).content(ctx.xml())).andExpect(status().isForbidden());
        mvc.perform(get("/api/notas-entrada/importacoes").header("Authorization",operador)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notas-entrada/importacoes").header("Authorization",t.tokenGlobal())).andExpect(status().isForbidden());
    }

    private Contexto contexto() throws Exception {
        var t=tenant.criarTenantAdmin();
        var fornecedor=new Fornecedor(); fornecedor.setOrganizacaoId(t.organizacaoId()); fornecedor.setDocumento("11222333000181"); fornecedor.setNome("Fornecedor XML");
        long fornecedorId=fornecedores.saveAndFlush(fornecedor).getId();
        var marca=new Marca(); marca.setOrganizacaoId(t.organizacaoId()); marca.setNome("XML"); marcas.saveAndFlush(marca);
        var unidade=new UnidadeMedida(); unidade.setOrganizacaoId(t.organizacaoId()); unidade.setSigla("UN"); unidade.setNome("Unidade"); unidades.saveAndFlush(unidade);
        var produto=new Produto(); produto.setOrganizacaoId(t.organizacaoId()); produto.setNome("Produto XML"); produto.setMarca(marca); produto.setUnidadeMedida(unidade);
        long produtoId=produtos.saveAndFlush(produto).getId();
        String local=mvc.perform(post("/api/v1/stock-locations").header("Authorization",t.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Depósito XML\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long localId=JsonPath.<Number>read(local,"$.id").longValue();
        String destinatario=jdbc.queryForObject("select documento from organizacao where id=?",String.class,t.organizacaoId());
        return new Contexto(t,fornecedorId,produtoId,localId,NotaEntradaXmlParserTest.xml(destinatario));
    }
    private record Contexto(TenantFixture tenant,Long fornecedorId,Long produtoId,Long localId,String xml) { }
}
