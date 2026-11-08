package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class NotaEntradaFluxoUnicoTenantApiTest {

    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired FornecedorRepository fornecedores;
    @Autowired MarcaRepository marcas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ProdutoRepository produtos;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Autowired com.kaneko.estacionamento.estoque.EstoqueService estoque;
    @Autowired com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository condicoes;
    @Autowired NotaEntradaService notas;
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
    void validaDatasAoPreverCriarEEditarSemGerarEfeitos() throws Exception {
        LocalDate hoje = LocalDate.now();
        String corpo = """
                {"numero":"DATAS","fornecedorId":%d,"localEstoqueId":%d,"dataEmissao":"%s","dataChegada":"%s",
                 "itens":[{"produtoId":%d,"quantidade":1,"valorUnitario":10}]}
                """;
        String futura = corpo.formatted(fornecedorA, localA, hoje.plusDays(1), hoje.plusDays(1), produtoA);
        for (String rota : new String[]{"/api/notas-entrada/previa", "/api/notas-entrada"}) {
            mvc.perform(post(rota).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                            .contentType(MediaType.APPLICATION_JSON).content(futura))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("A data de emissão não pode ser futura."));
        }
        String chegadaAnterior = corpo.formatted(fornecedorA, localA, hoje, hoje.minusDays(1), produtoA);
        mvc.perform(post("/api/notas-entrada").header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(chegadaAnterior))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A chegada não pode ser anterior à emissão."));
        assertThat(contar("nota_entrada")).isZero();

        String mesmaData = corpo.formatted(fornecedorA, localA, hoje, hoje, produtoA);
        var criada = mvc.perform(post("/api/notas-entrada").header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(mesmaData))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.dataEmissao").value(hoje.toString()))
                .andReturn().getResponse();
        long id = JsonPath.<Number>read(criada.getContentAsString(), "$.id").longValue();
        mvc.perform(put("/api/notas-entrada/{id}", id).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, criada.getHeader(HttpHeaders.ETAG))
                        .contentType(MediaType.APPLICATION_JSON).content(futura))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A data de emissão não pode ser futura."));
        mvc.perform(get("/api/notas-entrada/{id}", id).header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dataEmissao").value(hoje.toString()));

        // Simula rascunho gravado antes da implantação da regra.
        jdbc.update("update nota_entrada set data_emissao=?, data_chegada=? where id=?",
                hoje.plusDays(1), hoje.plusDays(1), id);
        entityManager.clear();
        mvc.perform(post("/api/notas-entrada/{id}/confirmacao", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, criada.getHeader(HttpHeaders.ETAG)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A data de emissão não pode ser futura."));
        assertThat(jdbc.queryForObject("select situacao from nota_entrada where id=?", String.class, id))
                .isEqualTo("PENDENTE");
        assertThat(contar("movimento_estoque")).isZero();
        assertThat(contar("conta_pagar")).isZero();
    }

    @Test void recusaSeteCasasAntesDeGerarEfeitos() throws Exception {
        String body = payload("000120", fornecedorA, produtoA, localA)
                .replace("\"quantidade\":10", "\"quantidade\":1.0000001")
                .replace("\"valorUnitario\":5", "\"valorUnitario\":2,\"percentualDesconto\":0.000001");
        mvc.perform(post("/api/notas-entrada")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        assertThat(contar("nota_entrada")).isZero();
        assertThat(contar("movimento_estoque")).isZero();
        assertThat(contar("conta_pagar")).isZero();
    }

    @Test void baixaDeUmMilionesimoDeTituloGeradoPelaEntradaSemArredondar() throws Exception {
        String corpo = payload("BAIXA-MICROS", fornecedorA, produtoA, localA)
                .replace("\"quantidade\":10", "\"quantidade\":1.000001")
                .replace("\"valorUnitario\":5", "\"valorUnitario\":2,\"percentualDesconto\":0.000001");
        long nota = JsonPath.<Number>read(criarNota(corpo),
                "$.id").longValue();
        confirmar(nota, 0);
        mvc.perform(get("/api/v1/stock-balances").header("Authorization", tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].quantidade").value(1.000001));
        mvc.perform(get("/api/v1/stock-movements").header("Authorization", tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].delta").value(1.000001));
        long conta = jdbc.queryForObject("select id from conta_pagar where nota_entrada_id=?", Long.class, nota);
        mvc.perform(post("/api/contas-pagar/{id}/baixa", conta)
                        .header("Authorization", tenantA.tokenContextual())
                        .header("Idempotency-Key", "precisao-baixa-entrada")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":0.000001}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(2.000001));
        mvc.perform(get("/api/contas-pagar/resumo").header("Authorization", tenantA.tokenContextual())
                        .param("notaEntradaId", Long.toString(nota)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valorTotal").value(2.000002))
                .andExpect(jsonPath("$.saldo").value(2.000001));
        assertThat(jdbc.queryForObject("select valor_total_micros from nota_entrada where id=?", Long.class, nota))
                .isEqualTo(2000002L);
        assertThat(jdbc.queryForObject("select percentual_desconto_micros from item_nota_entrada where nota_entrada_id=?",
                Long.class, nota)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select valor_micros from parcela_nota_entrada where nota_entrada_id=?",
                Long.class, nota)).isEqualTo(2000002L);
        assertThat(jdbc.queryForObject("select quantidade_micros from saldo_estoque where produto_id=?",
                Long.class, produtoA)).isEqualTo(1000001L);
        assertThat(jdbc.queryForObject("select delta_micros from movimento_estoque where produto_id=?",
                Long.class, produtoA)).isEqualTo(1000001L);
        assertThat(jdbc.queryForObject("select valor_total_micros from conta_pagar where id=?", Long.class, conta))
                .isEqualTo(2000002L);
        assertThat(jdbc.queryForObject("select valor_desconto_micros + valor_juros_micros + valor_multa_micros"
                + " from conta_pagar where id=?", Long.class, conta)).isZero();
        assertThat(jdbc.queryForObject("select valor_micros from baixa_financeira where conta_pagar_id=?",
                Long.class, conta)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select valor_pago_micros from conta_pagar where id=?", Long.class, conta))
                .isEqualTo(1L);
        mvc.perform(post("/api/contas-pagar/{id}/baixa", conta)
                        .header("Authorization", tenantA.tokenContextual())
                        .header("Idempotency-Key", "precisao-baixa-sete-casas")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":0.0000001}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/contas-pagar/{id}/baixa", conta)
                        .header("Authorization", tenantA.tokenContextual())
                        .header("Idempotency-Key", "precisao-baixa-quitacao")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":2.000001}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("PAGA"))
                .andExpect(jsonPath("$.saldo").value(0));
        assertThat(jdbc.queryForObject("select sum(valor_micros) from baixa_financeira where conta_pagar_id=?",
                Long.class, conta)).isEqualTo(2000002L);
        assertThat(jdbc.queryForObject("select valor_pago_micros from conta_pagar where id=?", Long.class, conta))
                .isEqualTo(2000002L);
    }

    @Test void previaAplicaPercentualPreservaCustoUC401ESemLancamentos() throws Exception {
        String body = """
            {"numero":"PREVIA","fornecedorId":%d,"localEstoqueId":%d,"valorDesconto":10,
             "itens":[{"produtoId":%d,"quantidade":1,"valorUnitario":100,"percentualDesconto":10}]}
            """.formatted(fornecedorA,localA,produtoA);
        mvc.perform(post("/api/notas-entrada/previa").header("Authorization",tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nota.valorProdutos").value(90))
                .andExpect(jsonPath("$.valorObrigacao").value(80))
                .andExpect(jsonPath("$.valorCustoTotal").value(90))
                .andExpect(jsonPath("$.diferencaDescontoGeral").value(10))
                .andExpect(jsonPath("$.parcelas[0].valor").value(80))
                .andExpect(jsonPath("$.nota.itens[0].valorDesconto").value(10))
                .andExpect(jsonPath("$.nota.itens[0].unidadeMedida").value("NFA"));
        assertThat(contar("nota_entrada")).isZero();
        assertThat(contar("movimento_estoque")).isZero();
        assertThat(contar("conta_pagar")).isZero();
    }

    @Test
    void previaEConfirmacaoMostramCustoMedioPonderadoPorItem() throws Exception {
        estoque.registrarRecebimento(tenantA.organizacaoId(), tenantA.usuarioId(),
                produtoA, localA, new BigDecimal("10"), new BigDecimal("20.00"),
                "SALDO_INICIAL", "media:inicial", "Saldo anterior para custo medio");
        String body = payload("CUSTO-MEDIO", fornecedorA, produtoA, localA)
                .replace("\"quantidade\":10", "\"quantidade\":5")
                .replace("\"valorUnitario\":5", "\"valorUnitario\":30");

        mvc.perform(post("/api/notas-entrada/previa")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.custosMedios[0].saldoAnterior").value(10))
                .andExpect(jsonPath("$.custosMedios[0].custoMedioAnterior").value(20.00))
                .andExpect(jsonPath("$.custosMedios[0].quantidadeEntrada").value(5))
                .andExpect(jsonPath("$.custosMedios[0].custoUnitarioEntrada").value(30.00))
                .andExpect(jsonPath("$.custosMedios[0].custoMedioPosterior").value(23.333333))
                .andExpect(jsonPath("$.custosMedios[0].aplicaAoEstoque").value(true))
                .andExpect(jsonPath("$.custosMedios[0].projecao").value(true));

        long id = JsonPath.<Number>read(criarNota(body), "$.id").longValue();
        confirmar(id, 0);
        mvc.perform(get("/api/notas-entrada/{id}/detalhes", id)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previa.custosMedios[0].custoMedioPosterior").value(23.333333))
                .andExpect(jsonPath("$.previa.custosMedios[0].projecao").value(false));
        assertThat(jdbc.queryForObject("SELECT custo_micros * 0.000001 FROM produto WHERE id=?",
                BigDecimal.class, produtoA)).isEqualByComparingTo("23.333333");
        assertThat(jdbc.queryForObject("""
                SELECT custo_medio_posterior_micros * 0.000001 FROM item_nota_entrada
                WHERE nota_entrada_id=?
                """, BigDecimal.class, id)).isEqualByComparingTo("23.333333");
    }

    @Test void editaPendenteFiltraNoServidorERecusaConfirmarRevisaoAntiga() throws Exception {
        String inicial = payload("EDITAR",fornecedorA,produtoA,localA);
        String criada = criarNota(inicial);
        long id=JsonPath.<Number>read(criada,"$.id").longValue();
        mvc.perform(put("/api/notas-entrada/{id}",id).header("Authorization",tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content(inicial)).andExpect(status().isPreconditionRequired());
        String editada = mvc.perform(put("/api/notas-entrada/{id}",id)
                .header("Authorization",tenantA.tokenContextual()).header("If-Match","\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(inicial.replace("\"quantidade\":10","\"quantidade\":2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valorTotal").value(10))
                .andReturn().getResponse().getContentAsString();
        long versao=JsonPath.<Number>read(editada,"$.version").longValue();
        assertThat(versao).isPositive();
        mvc.perform(post("/api/notas-entrada/{id}/confirmacao",id).header("Authorization",tenantA.tokenContextual()))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/notas-entrada/{id}/confirmacao",id).header("Authorization",tenantA.tokenContextual())
                .header("If-Match","\"0\"")).andExpect(status().isPreconditionFailed());
        confirmar(id,versao);
        criarNota(payload("OUTRA",fornecedorA,produtoA,localA));
        mvc.perform(get("/api/notas-entrada").header("Authorization",tenantA.tokenContextual())
                .param("numero","edit").param("situacao","CONFIRMADA").param("fornecedorId",fornecedorA.toString())
                .param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        assertThat(contar("conta_pagar")).isEqualTo(1);
        assertThat(contar("movimento_estoque")).isEqualTo(1);
    }

    @Test void rejeitaEscalasPerdidasEDescontosContraditorios() throws Exception {
        String body=payload("ESCALA",fornecedorA,produtoA,localA);
        for(String invalido:java.util.List.of(body.replace("\"quantidade\":10","\"quantidade\":0.0000004"),
                body.replace("\"valorUnitario\":5","\"valorUnitario\":5.0000001"),
                body.replace("\"valorUnitario\":5","\"valorUnitario\":5,\"valorDesconto\":1,\"percentualDesconto\":10"))) {
            mvc.perform(post("/api/notas-entrada").header("Authorization",tenantA.tokenContextual())
                    .contentType(MediaType.APPLICATION_JSON).content(invalido)).andExpect(status().isConflict());
        }
        assertThat(contar("nota_entrada")).isZero();
    }

    @Test void parcelaPequenaNaoViraNegativaEZeroNaoGeraDivida() throws Exception {
        var condicao=new com.kaneko.estacionamento.pagamento.CondicaoPagamento();
        condicao.setOrganizacaoId(tenantA.organizacaoId()); condicao.setNome("Quatro partes"); condicao.setNumeroParcelas(4);
        for(int i=1;i<=4;i++) {
            var p=new com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento();
            p.setOrganizacaoId(tenantA.organizacaoId()); p.setCondicaoPagamento(condicao);
            p.setNumero(i); p.setDias(i*10); p.setPercentual(new BigDecimal("25")); condicao.getParcelas().add(p);
        }
        condicoes.saveAndFlush(condicao);
        String body="""
            {"numero":"CENTAVOS","fornecedorId":%d,"localEstoqueId":%d,"condicaoPagamentoId":%d,
             "itens":[{"produtoId":%d,"quantidade":1,"valorUnitario":0.02}]}
            """.formatted(fornecedorA,localA,condicao.getId(),produtoA);
        mvc.perform(post("/api/notas-entrada/previa").header("Authorization",tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelas.length()").value(4)).andExpect(jsonPath("$.parcelas[3].valor").value(0.005));
        String criada=criarNota(body);long id=JsonPath.<Number>read(criada,"$.id").longValue();
        // O cronograma da revisao nao muda com edicao posterior do cadastro.
        condicao.getParcelas().getFirst().setDias(99); condicoes.saveAndFlush(condicao);
        mvc.perform(post("/api/notas-entrada/{id}/previa",id).header("Authorization",tenantA.tokenContextual())
                .header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcelas[0].dataVencimento")
                        .value(java.time.LocalDate.now().plusDays(10).toString()));
        confirmar(id,0);
        assertThat(contar("conta_pagar")).isEqualTo(4);
        assertThat(jdbc.queryForObject("select sum(valor_total_micros) * 0.000001 from conta_pagar where nota_entrada_id=?",BigDecimal.class,id))
                .isEqualByComparingTo("0.02");
        assertThat(jdbc.queryForObject("select min(data_vencimento-data_emissao) from conta_pagar where nota_entrada_id=?",Integer.class,id))
                .isEqualTo(10);
        String zero=criarNota(body.replace("CENTAVOS","ZERO").replace("0.02","0.00"));
        confirmar(JsonPath.<Number>read(zero,"$.id").longValue(),0);
        assertThat(contar("conta_pagar")).isEqualTo(4);
    }

    @Test void pendenteAnteriorV78ExigePutMesmoSemEdicaoAntesDeRevisarEConfirmar() throws Exception {
        var condicao=new com.kaneko.estacionamento.pagamento.CondicaoPagamento();
        condicao.setOrganizacaoId(tenantA.organizacaoId()); condicao.setNome("Legada trinta dias");
        condicao.setNumeroParcelas(1);
        var parcela=new com.kaneko.estacionamento.pagamento.ParcelaCondicaoPagamento();
        parcela.setOrganizacaoId(tenantA.organizacaoId()); parcela.setCondicaoPagamento(condicao);
        parcela.setNumero(1); parcela.setDias(30); parcela.setPercentual(new BigDecimal("100"));
        condicao.getParcelas().add(parcela); condicoes.saveAndFlush(condicao);
        Long condicaoId=condicao.getId();
        String body=payload("PENDENTE-V77",fornecedorA,produtoA,localA)
                .replace("\"itens\":", "\"dataEmissao\":\"2026-01-01\",\"condicaoPagamentoId\":"+condicaoId+",\"itens\":");
        long id=JsonPath.<Number>read(criarNota(body),"$.id").longValue();
        // Representa a pendente pre-V78: nenhum snapshot/backfill e mesma revisao da nota.
        jdbc.update("delete from parcela_nota_entrada where nota_entrada_id=?",id);
        jdbc.update("update nota_entrada set cronograma_definido=false where id=?",id);
        entityManager.clear();
        String recusada=mvc.perform(post("/api/notas-entrada/{id}/previa",id)
                .header("Authorization",tenantA.tokenContextual()).header("If-Match","\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(recusada).contains("Salve o rascunho");
        mvc.perform(post("/api/notas-entrada/{id}/confirmacao",id)
                .header("Authorization",tenantA.tokenContextual()).header("If-Match","\"0\""))
                .andExpect(status().isConflict());
        assertThat(contar("movimento_estoque")).isZero();
        assertThat(contar("conta_pagar")).isZero();
        assertThat(jdbc.queryForObject("select version from nota_entrada where id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select cronograma_definido from nota_entrada where id=?",Boolean.class,id)).isFalse();
        String salva=mvc.perform(put("/api/notas-entrada/{id}",id)
                .header("Authorization",tenantA.tokenContextual()).header("If-Match","\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long versao=JsonPath.<Number>read(salva,"$.version").longValue();
        assertThat(versao).isPositive();
        assertThat(jdbc.queryForObject("select cronograma_definido from nota_entrada where id=?",Boolean.class,id)).isTrue();
        jdbc.update("update parcela_condicao_pagamento set dias=99 where condicao_pagamento_id=?",condicaoId);
        entityManager.clear();
        mvc.perform(post("/api/notas-entrada/{id}/previa",id)
                .header("Authorization",tenantA.tokenContextual()).header("If-Match","\""+versao+"\"")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcelas.length()").value(1))
                .andExpect(jsonPath("$.parcelas[0].valor").value(50))
                .andExpect(jsonPath("$.parcelas[0].dataVencimento").value("2026-01-31"));
        confirmar(id,versao);
        assertThat(jdbc.queryForObject("select data_vencimento from conta_pagar where nota_entrada_id=?",
                java.time.LocalDate.class,id)).isEqualTo(java.time.LocalDate.of(2026,1,31));
        assertThat(contar("movimento_estoque")).isEqualTo(1);
        assertThat(contar("conta_pagar")).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void falhaAoGerarTituloReverteEstoqueCustoEConfirmacao() throws Exception {
        BigDecimal custoAntes=produtos.findById(produtoA).orElseThrow().getCusto();
        long id=JsonPath.<Number>read(criarNota(payload("ROLLBACK",fornecedorA,produtoA,localA)),"$.id").longValue();
        // Simula cronograma legado inconsistente; o título falhará DEPOIS da entrada física.
        jdbc.update("update parcela_nota_entrada set data_vencimento=current_date-1 where nota_entrada_id=?",id);
        mvc.perform(post("/api/notas-entrada/{id}/confirmacao",id).header("Authorization",tenantA.tokenContextual())
                .header("If-Match","\"0\"")).andExpect(status().isConflict());
        assertThat(contar("movimento_estoque")).isZero();
        assertThat(contar("saldo_estoque")).isZero();
        assertThat(contar("conta_pagar")).isZero();
        assertThat(produtos.findById(produtoA).orElseThrow().getCusto()).isEqualByComparingTo(custoAntes);
        assertThat(jdbc.queryForObject("select situacao from nota_entrada where id=?",String.class,id)).isEqualTo("PENDENTE");
    }

    private String criarNota(String body) throws Exception {
        return mvc.perform(post("/api/notas-entrada").header("Authorization",tenantA.tokenContextual())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
    private void confirmar(long id,long versao) throws Exception {
        mvc.perform(post("/api/notas-entrada/{id}/confirmacao",id).header("Authorization",tenantA.tokenContextual())
                .header("If-Match","\""+versao+"\"")).andExpect(status().isOk());
    }
    private int contar(String tabela) {
        return jdbc.queryForObject("select count(*) from "+tabela+" where organizacao_id=?",Integer.class,tenantA.organizacaoId());
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
