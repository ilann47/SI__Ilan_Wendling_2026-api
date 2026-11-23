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
import java.time.LocalDate;
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

    @Autowired com.kaneko.estacionamento.estoque.EstoqueService estoque;
    @Autowired com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository condicoesParaTeste;

    @Test
    void rejeitaXmlComEmissaoFuturaAntesDePersistir() throws Exception {
        var ctx = contexto();
        String futuro = ctx.xml().replaceFirst("(?<=<dhEmi>)\\d{4}-\\d{2}-\\d{2}", LocalDate.now().plusDays(1).toString());
        mvc.perform(post("/api/notas-entrada/importacoes").header("Authorization", ctx.tenant().tokenContextual())
                        .contentType(MediaType.APPLICATION_XML).content(futuro))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A data de emissão não pode ser futura."));
        for (String tabela : new String[]{"importacao_nota_entrada", "nota_entrada", "movimento_estoque", "conta_pagar"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabela + " where organizacao_id=?",
                    Integer.class, ctx.tenant().organizacaoId())).isZero();
        }
    }

    @Test void importaConciliaConfereELancaSemDuplicar() throws Exception {
        var t = tenant.criarTenantAdmin();
        String documento = jdbc.queryForObject("select documento from organizacao where id=?", String.class, t.organizacaoId());
        String xml = NotaEntradaXmlParserTest.xml(documento)
                .replace("<qCom>2</qCom>", "<qCom>2.000001</qCom>")
                .replace("<vUnCom>10.00</vUnCom>", "<vUnCom>10.000001</vUnCom>");
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
        mvc.perform(get(base+"/previa").header("Authorization",t.tokenContextual())).andExpect(status().isConflict());
        String conciliar = "{\"fornecedorId\":" + fornecedorId + ",\"localEstoqueId\":" + localId
                + ",\"itens\":[{\"sequencia\":1,\"produtoId\":" + produtoId + ",\"fatorConversao\":1,\"valorUnitario\":10.000001}]}";
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
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"sequencia\":1,\"quantidadeConferida\":2.000001}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFERIDA"));
        mvc.perform(get(base+"/previa").header("Authorization",t.tokenContextual())).andExpect(status().isOk())
                .andExpect(jsonPath("$.valorObrigacao").value(20)).andExpect(jsonPath("$.valorCustoTotal").value(20))
                .andExpect(jsonPath("$.parcelas[0].valor").value(20));
        mvc.perform(post(base + "/confirmacao").header("Authorization", t.tokenContextual()).header("If-Match", "\"3\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.notaEntradaId").isNumber());
        mvc.perform(post(base + "/confirmacao").header("Authorization", t.tokenContextual()).header("If-Match", "\"3\""))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select quantidade_micros * 0.000001 from saldo_estoque where organizacao_id=? and produto_id=? and local_estoque_id=?",
                BigDecimal.class, t.organizacaoId(), produtoId, localId)).isEqualByComparingTo("2.000001");
        assertThat(jdbc.queryForObject("select quantidade_conferida_micros from item_importacao_nota_entrada where importacao_id=?",
                Long.class, id)).isEqualTo(2000001L);
        assertThat(jdbc.queryForObject("select valor_unitario_micros from item_nota_entrada where produto_id=?",
                Long.class, produtoId)).isEqualTo(10000001L);
        assertThat(jdbc.queryForObject("select valor_total_micros from conta_pagar where organizacao_id=?",
                Long.class, t.organizacaoId())).isEqualTo(20000000L);
        assertThat(jdbc.queryForObject("select count(*) from conta_pagar where organizacao_id=?", Integer.class, t.organizacaoId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from nota_entrada where organizacao_id=?", Integer.class, t.organizacaoId())).isEqualTo(1);
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

    @Test void cronogramaXMLConferidoNaoMudaComAlteracaoPosteriorDaCondicao() throws Exception {
        var ctx=contexto(); var t=ctx.tenant();
        var condicao=new com.kaneko.estacionamento.pagamento.CondicaoPagamento();
        condicao.setOrganizacaoId(t.organizacaoId()); condicao.setNome("XML duas parcelas"); condicao.setNumeroParcelas(2);
        for(int numero=1;numero<=2;numero++) {
            var p=new com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento();
            p.setOrganizacaoId(t.organizacaoId());p.setCondicaoPagamento(condicao);p.setNumero(numero);
            p.setDias(numero*30);p.setPercentual(new BigDecimal("50"));condicao.getParcelas().add(p);
        }
        var repoCondicao=condicoesParaTeste;
        repoCondicao.saveAndFlush(condicao);
        String importada=mvc.perform(post("/api/notas-entrada/importacoes").header("Authorization",t.tokenContextual())
                .contentType(MediaType.APPLICATION_XML).content(ctx.xml())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id=JsonPath.<Number>read(importada,"$.id").longValue();
        String base="/api/notas-entrada/importacoes/"+id;
        String body="{\"fornecedorId\":"+ctx.fornecedorId()+",\"localEstoqueId\":"+ctx.localId()+",\"condicaoPagamentoId\":"+condicao.getId()
                +",\"itens\":[{\"sequencia\":1,\"produtoId\":"+ctx.produtoId()+",\"fatorConversao\":1,\"valorUnitario\":10}]}";
        mvc.perform(put(base+"/conciliacao").header("Authorization",t.tokenContextual()).header("If-Match","\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post(base+"/conferencia").header("Authorization",t.tokenContextual()).header("If-Match","\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[{\"sequencia\":1,\"quantidadeConferida\":2}]}"))
                .andExpect(status().isOk());
        String previaAntes=mvc.perform(get(base+"/previa").header("Authorization",t.tokenContextual())).andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelas.length()").value(2)).andReturn().getResponse().getContentAsString();
        condicao.getParcelas().getFirst().setDias(99);repoCondicao.saveAndFlush(condicao);
        String previaDepois=mvc.perform(get(base+"/previa").header("Authorization",t.tokenContextual())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Object>read(previaDepois,"$.parcelas")).isEqualTo(JsonPath.<Object>read(previaAntes,"$.parcelas"));
        String confirmada=mvc.perform(post(base+"/confirmacao").header("Authorization",t.tokenContextual()).header("If-Match","\"2\""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long nota=JsonPath.<Number>read(confirmada,"$.notaEntradaId").longValue();
        assertThat(jdbc.queryForObject("select min(data_vencimento-data_emissao) from conta_pagar where nota_entrada_id=?",Integer.class,nota)).isEqualTo(30);
        assertThat(jdbc.queryForObject("select count(*) from conta_pagar where nota_entrada_id=?",Integer.class,nota)).isEqualTo(2);
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
