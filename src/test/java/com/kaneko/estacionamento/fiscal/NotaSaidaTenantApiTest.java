package com.kaneko.estacionamento.fiscal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class NotaSaidaTenantApiTest {
    @Autowired MockMvc mvc;
    @Autowired TenantTestSupport tenant;
    @Autowired ClienteRepository clientes;
    @Autowired MarcaRepository marcas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ProdutoRepository produtos;
    @Autowired IdempotenciaRepository idempotencia;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.kaneko.estacionamento.estoque.EstoqueService estoque;

    private TenantFixture fixture;
    private Long clienteId;
    private Long produtoId;
    private Long localId;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
        Cliente cliente = new Cliente();
        cliente.setOrganizacaoId(fixture.organizacaoId());
        cliente.setNome("Cliente saida");
        cliente.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        clienteId = clientes.saveAndFlush(cliente).getId();
        Marca marca = new Marca(); marca.setOrganizacaoId(fixture.organizacaoId());
        marca.setNome("Marca saida"); marca = marcas.save(marca);
        UnidadeMedida unidade = new UnidadeMedida(); unidade.setOrganizacaoId(fixture.organizacaoId());
        unidade.setNome("Unidade saida"); unidade.setSigla("NS"); unidade = unidades.save(unidade);
        Produto produto = new Produto(); produto.setOrganizacaoId(fixture.organizacaoId());
        produto.setNome("Produto saida"); produto.setMarca(marca); produto.setUnidadeMedida(unidade);
        produtoId = produtos.saveAndFlush(produto).getId();
        localId = id(post("/api/v1/stock-locations"), "{\"nome\":\"Expedicao\"}");
        estoque.registrarRecebimento(fixture.organizacaoId(), fixture.usuarioId(), produtoId, localId,
                BigDecimal.TEN, BigDecimal.ZERO, "NOTA_ENTRADA", "1:1", "Carga inicial de teste");
    }

    @Test
    void cancelamentoEExclusaoNaoUsamUmaVersaoAntiga() throws Exception {
        var corpo = dados("NS-CANCELAR", "", "{\"produtoId\":" + produtoId
                + ",\"quantidade\":2,\"valorUnitario\":10}");
        long nota = id(post("/api/notas-saida"), corpo);
        mvc.perform(post("/api/notas-saida/{id}/cancelamento", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(put("/api/notas-saida/{id}", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
        mvc.perform(post("/api/notas-saida/{id}/cancelamento", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(delete("/api/notas-saida/{id}", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(post("/api/notas-saida/{id}/cancelamento", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CANCELADA"));
        mvc.perform(delete("/api/notas-saida/{id}", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(status().isNoContent());
    }

    @Test
    void confirmaSaidaBaixaRazaoEGeraContaReceber() throws Exception {
        long nota = id(post("/api/notas-saida"), "{\"numero\":\"NS-1\",\"clienteId\":"
                + clienteId + ",\"localEstoqueId\":" + localId + ",\"itens\":[{\"produtoId\":"
                + produtoId + ",\"quantidade\":4,\"valorUnitario\":8}]}");
        mvc.perform(post("/api/notas-saida/{id}/confirmacao", nota)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CONFIRMADA"));
        mvc.perform(get("/api/v1/stock-balances")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .param("produtoId", produtoId.toString()).param("localEstoqueId", localId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].quantidade").value(6));
        mvc.perform(get("/api/contas-receber")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].notaSaidaId").value(nota))
                .andExpect(jsonPath("$.content[0].vendaAdministrativaId").doesNotHaveJsonPath());
    }

    @Test
    void preservaMilionesimosDaSaidaAteQuitacao() throws Exception {
        String item = "{\"produtoId\":" + produtoId
                + ",\"quantidade\":1.000001,\"valorUnitario\":2,\"percentualDesconto\":0.000001}";
        long nota = id(post("/api/notas-saida"), dados("NS-MICROS", "", item));
        String auth = fixture.tokenContextual();
        mvc.perform(post("/api/notas-saida/{id}/confirmacao", nota)
                .header(HttpHeaders.IF_MATCH, "\"0\"").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valorTotal").value(2.000002));
        mvc.perform(get("/api/v1/stock-balances").param("produtoId", produtoId.toString())
                .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].quantidade").value(8.999999));
        long conta = jdbc.queryForObject("select id from conta_receber where nota_saida_id=?", Long.class, nota);
        for (String valor : new String[]{"0.000001", "0.000001"}) {
            mvc.perform(post("/api/contas-receber/{id}/baixa", conta).header(HttpHeaders.AUTHORIZATION, auth)
                    .header("Idempotency-Key", "saida-parcial").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"valor\":" + valor + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(2.000001));
        }
        mvc.perform(get("/api/contas-receber/resumo").param("notaSaidaId", Long.toString(nota))
                .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(2.000001));
        mvc.perform(post("/api/contas-receber/{id}/baixa", conta).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":0.0000001}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/contas-receber/{id}/baixa", conta).header(HttpHeaders.AUTHORIZATION, auth)
                .header("Idempotency-Key", "saida-final").contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":2.000001}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(0))
                .andExpect(jsonPath("$.situacao").value("RECEBIDA"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForList(
                "select valor_micros from baixa_financeira where conta_receber_id=? order by valor_micros",
                Long.class, conta)).containsExactly(1L, 2000001L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select valor_recebido_micros from conta_receber where id=?", Long.class, conta)).isEqualTo(2000002L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select percentual_desconto_micros from item_nota_saida where nota_saida_id=?", Long.class, nota)).isEqualTo(1L);
    }

    @Test
    void rejeitaCampoDePedidoRemovidoSemCriarNemEditarNota() throws Exception {
        String item = "{\"produtoId\":" + produtoId + ",\"quantidade\":1,\"valorUnitario\":10}";
        String corpoLegado = dados("NS-VINCULO-REMOVIDO", ",\"vendaAdministrativaId\":9", item);
        mvc.perform(post("/api/notas-saida")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header("Idempotency-Key", "vinculo-removido")
                        .contentType(MediaType.APPLICATION_JSON).content(corpoLegado))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notas-saida")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        long nota = id(post("/api/notas-saida"), dados("NS-DIRETA", "", item));
        mvc.perform(put("/api/notas-saida/{id}", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(corpoLegado))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notas-saida/{id}", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("NS-DIRETA"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.vendaAdministrativaId").doesNotHaveJsonPath());
    }

    @Test
    void recuperaNotaAvulsaRegistradaComHashAnteriorAV82() throws Exception {
        String item = "{\"produtoId\":" + produtoId + ",\"quantidade\":1,\"valorUnitario\":10}";
        String corpo = dados("NS-HASH-V81", "", item);
        long nota = id(post("/api/notas-saida"), corpo);
        // Formato do record antes da remoção do campo de vínculo, gravado em V81.
        String representacaoV81 = ("NotaSaidaRequest[numero=NS-HASH-V81, modelo=null, serie=null, "
                + "clienteId=%d, condicaoPagamentoId=null, localEstoqueId=%d, vendaAdministrativaId=null, "
                + "dataEmissao=null, dataSaida=null, tipoFrete=null, valorFrete=null, valorSeguro=null, "
                + "outrasDespesas=null, valorDesconto=null, observacao=null, itens=[ItemNotaSaidaRequest["
                + "produtoId=%d, quantidade=1, valorUnitario=10, valorDesconto=null, percentualDesconto=null]]]")
                .formatted(clienteId, localId, produtoId);
        String hashV81 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(representacaoV81.getBytes(StandardCharsets.UTF_8)));
        idempotencia.registrar(fixture.organizacaoId(), fixture.usuarioId(), "CRIAR_NOTA_SAIDA",
                "nota-existente-v81", hashV81, "NOTA_SAIDA", nota);

        mvc.perform(post("/api/notas-saida")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header("Idempotency-Key", "nota-existente-v81")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(nota));
        mvc.perform(post("/api/notas-saida")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header("Idempotency-Key", "nota-existente-v81")
                        .contentType(MediaType.APPLICATION_JSON).content(dados("NS-OUTRA", "", item)))
                .andExpect(status().isConflict());
    }

    @Test
    void descontoPercentualERateioFechamComOValorDaNota() throws Exception {
        String item = "{\"produtoId\":" + produtoId
                + ",\"quantidade\":1,\"valorUnitario\":100,\"percentualDesconto\":10}";
        mvc.perform(post("/api/notas-saida")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dados("NS-CENTAVOS", ",\"valorFrete\":0.01", item + "," + item + "," + item)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorTotal").value(270.01))
                .andExpect(jsonPath("$.itens[0].valorDesconto").value(10))
                .andExpect(jsonPath("$.itens[0].rateioFrete").value(0.003334))
                .andExpect(jsonPath("$.itens[1].rateioFrete").value(0.003333))
                .andExpect(jsonPath("$.itens[2].rateioFrete").value(0.003333));
    }

    @Test
    void exigeRevisaoAtualEUsaCustoDaConfirmacao() throws Exception {
        var item = "{\"produtoId\":" + produtoId + ",\"quantidade\":2,\"valorUnitario\":10}";
        var corpo = dados("NS-CUSTO", "", item);
        long nota = id(post("/api/notas-saida"), corpo);
        Produto produto = produtos.findById(produtoId).orElseThrow();
        produto.setCusto(new BigDecimal("4.25"));
        produtos.saveAndFlush(produto);
        mvc.perform(post("/api/notas-saida/{id}/confirmacao", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(put("/api/notas-saida/{id}", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        produto = produtos.findById(produtoId).orElseThrow();
        produto.setCusto(new BigDecimal("5.75"));
        produtos.saveAndFlush(produto);
        mvc.perform(post("/api/notas-saida/{id}/confirmacao", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(post("/api/notas-saida/{id}/confirmacao", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].custoPrecoFinal").value(5.75));
        mvc.perform(get("/api/notas-saida/{id}/detalhes", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origemEfeitos").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.parcelas[0].valor").value(20))
                .andExpect(jsonPath("$.contasReceber[0].valorOriginal").value(20))
                .andExpect(jsonPath("$.movimentosEstoque[0].custoUnitario").value(5.75));
    }

    private String dados(String numero, String extras, String itens) {
        return "{\"numero\":\"" + numero + "\",\"clienteId\":" + clienteId
                + ",\"localEstoqueId\":" + localId + extras + ",\"itens\":[" + itens + "]}";
    }

    @Test
    void confirmacaoUsaParcelasCongeladasApesarDeEdicaoDaCondicao() throws Exception {
        String condicao = """
                {"nome":"Duas parcelas","numeroParcelas":2,
                 "parcelas":[{"numero":1,"dias":30,"percentual":50},{"numero":2,"dias":60,"percentual":50}]}
                """;
        long condicaoId = id(post("/api/condicoes-pagamento"), condicao);
        long nota = id(post("/api/notas-saida"), dados("NS-PARCELA",
                ",\"condicaoPagamentoId\":" + condicaoId + ",\"dataEmissao\":\"2026-11-01\"",
                "{\"produtoId\":" + produtoId + ",\"quantidade\":1,\"valorUnitario\":0.01}"));
        mvc.perform(put("/api/condicoes-pagamento/{id}", condicaoId)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condicao.replace("\"dias\":30", "\"dias\":15")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/notas-saida/{id}/confirmacao", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notas-saida/{id}/detalhes", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelas.length()").value(2))
                .andExpect(jsonPath("$.parcelas[0].dataVencimento").value("2026-12-01"))
                .andExpect(jsonPath("$.parcelas[0].valor").value(0.005))
                .andExpect(jsonPath("$.parcelas[1].valor").value(0.005))
                .andExpect(jsonPath("$.contasReceber.length()").value(2))
                .andExpect(jsonPath("$.contasReceber[0].dataVencimento").value("2026-12-01"))
                .andExpect(jsonPath("$.contasReceber[0].valorOriginal").value(0.005))
                .andExpect(jsonPath("$.contasReceber[1].valorOriginal").value(0.005));
    }

    @Test
    void numeroSerieModeloSaoDoEmitenteENaoDoCliente() throws Exception {
        Cliente outro = new Cliente();
        outro.setOrganizacaoId(fixture.organizacaoId());
        outro.setNome("Outro cliente");
        outro.setDocumento(UUID.randomUUID().toString().replace("-", "").substring(0, 11));
        Long outroId = clientes.saveAndFlush(outro).getId();
        String item = "{\"produtoId\":" + produtoId + ",\"quantidade\":1,\"valorUnitario\":10}";
        id(post("/api/notas-saida"), dados("00123", ",\"serie\":\"001\",\"modelo\":\"55\"", item));
        String repetida = dados("123", ",\"serie\":\"1\",\"modelo\":\"055\"", item)
                .replace("\"clienteId\":" + clienteId, "\"clienteId\":" + outroId);
        mvc.perform(post("/api/notas-saida")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(repetida))
                .andExpect(status().isConflict());
        id(post("/api/notas-saida"), dados("123", ",\"modelo\":\"65\"", item));
    }

    @Test
    void semObrigacaoNaoGeraTituloEPedidoIdempotenteRecuperaRascunho() throws Exception {
        String corpo = dados("NS-ZERO", "", "{\"produtoId\":" + produtoId
                + ",\"quantidade\":1,\"valorUnitario\":5,\"percentualDesconto\":100}");
        long nota = id(post("/api/notas-saida").header("Idempotency-Key", "rascunho-zero"), corpo);
        mvc.perform(post("/api/notas-saida")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header("Idempotency-Key", "rascunho-zero")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(nota));
        mvc.perform(post("/api/notas-saida/{id}/confirmacao", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notas-saida/{id}/detalhes", nota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelas.length()").value(0))
                .andExpect(jsonPath("$.contasReceber.length()").value(0))
                .andExpect(jsonPath("$.movimentosEstoque.length()").value(1));
    }

    private long id(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String payload) throws Exception {
        String body = mvc.perform(request.header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }
}
