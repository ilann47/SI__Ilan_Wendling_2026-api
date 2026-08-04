package com.kaneko.estacionamento.estoque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class EstoqueTenantApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private MarcaRepository marcas;
    @Autowired private UnidadeMedidaRepository unidades;
    @Autowired private ProdutoRepository produtos;
    @Autowired private JdbcTemplate jdbc;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long produtoA;
    private Long produtoB;
    private Long localA;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        produtoA = criarProduto(tenantA.organizacaoId(), "A");
        produtoB = criarProduto(tenantB.organizacaoId(), "B");
        localA = criarLocal(tenantA, "Estoque principal");
    }

    @Test
    void localTemContratoFechadoEtagsEIsolamento() throws Exception {
        Long localB = criarLocal(tenantB, "Estoque principal");
        mvc.perform(get("/api/v1/stock-locations/{id}", localB)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/stock-locations")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hostil\",\"organizacaoId\":999}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/stock-locations/{id}", localA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Estoque central\"}"))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(put("/api/v1/stock-locations/{id}", localA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"9\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Estoque central\"}"))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(put("/api/v1/stock-locations/{id}", localA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Estoque central\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.nome").value("Estoque central"));
    }

    @Test
    void exigeStockPermissionEJwtContextual() throws Exception {
        mvc.perform(get("/api/v1/stock-locations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/stock-locations")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());
        String login = "stock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(tenantA.organizacaoId(), operador.getId(),
                tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(tenantA.organizacaoId(),
                tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/v1/stock-balances")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void ajusteEIdempotenteExibeNomesEPosicaoAgregada() throws Exception {
        String payload = "{\"produtoId\":" + produtoA + ",\"localEstoqueId\":" + localA
                + ",\"delta\":2.500,\"custoUnitario\":12.30,"
                + "\"motivo\":\"Contagem inicial\"}";
        String primeira = mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "ajuste-1")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produto").value("Produto Estoque A"))
                .andExpect(jsonPath("$.localEstoque").value("Estoque principal"))
                .andExpect(jsonPath("$.delta").value(2.5))
                .andReturn().getResponse().getContentAsString();
        long movimento = ((Number) JsonPath.read(primeira, "$.id")).longValue();
        mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "ajuste-1")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(movimento));
        mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "ajuste-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste(produtoA, localA, "3", "Outro payload")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/stock-balances")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString())
                        .param("localEstoqueId", localA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].quantidade").value(2.5));
        mvc.perform(get("/api/v1/stock-positions")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].quantidade").value(2.5))
                .andExpect(jsonPath("$.content[0].abaixoMinimo").value(false));
        Produto persistido = produtos.findById(produtoA).orElseThrow();
        assertThat(persistido.getQuantidade()).isEqualByComparingTo("0");
        assertThat(persistido.getVersion()).isZero();

        String evento = jdbc.queryForObject("""
                SELECT payload::text FROM evento_outbox
                WHERE aggregate_type='MOVIMENTO_ESTOQUE' AND aggregate_id=?
                  AND event_type='EstoqueAjustado'
                """, String.class, movimento);
        Map<String, Object> payloadEvento = JsonPath.read(evento, "$");
        assertThat(payloadEvento).containsKeys("movimentoId", "produtoId", "produto",
                "localEstoqueId", "localEstoque", "saldoEstoqueId", "tipo", "delta",
                "custoUnitario", "origemTipo", "origemChave", "atorId", "motivo",
                "saldoAnterior", "saldoPosterior", "compensadoId");
        assertThat(((Number) payloadEvento.get("movimentoId")).longValue()).isEqualTo(movimento);
        assertThat(((Number) payloadEvento.get("produtoId")).longValue()).isEqualTo(produtoA);
        assertThat(((Number) payloadEvento.get("localEstoqueId")).longValue()).isEqualTo(localA);
        assertThat(payloadEvento).containsEntry("produto", "Produto Estoque A")
                .containsEntry("localEstoque", "Estoque principal")
                .containsEntry("tipo", "AJUSTE")
                .containsEntry("origemTipo", "AJUSTE_MANUAL")
                .containsEntry("motivo", "Contagem inicial")
                .containsEntry("compensadoId", null);
        assertThat(new BigDecimal(payloadEvento.get("delta").toString()))
                .isEqualByComparingTo("2.5");
        assertThat(new BigDecimal(payloadEvento.get("custoUnitario").toString()))
                .isEqualByComparingTo("12.3");
        String auditoria = jdbc.queryForObject("""
                SELECT estado_posterior::text FROM registro_auditoria
                WHERE recurso_tipo='MOVIMENTO_ESTOQUE' AND recurso_id=?
                ORDER BY id DESC LIMIT 1
                """, String.class, movimento);
        Map<String, Object> payloadAuditoria = JsonPath.read(auditoria, "$");
        assertThat(payloadAuditoria).containsKeys("movimentoId", "saldoAnterior",
                "saldoPosterior", "origemTipo", "atorId", "produto", "localEstoque");
        assertThat(((Number) payloadAuditoria.get("movimentoId")).longValue())
                .isEqualTo(movimento);
        assertThat(((Number) payloadAuditoria.get("atorId")).longValue())
                .isEqualTo(tenantA.usuarioId());
    }

    @Test
    void mesmaChaveDeIdempotenciaEIndependentePorAtor() throws Exception {
        String login = "stock_actor_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10);
        Usuario outro = tenant.criarUsuario(login, PerfilUsuario.ADMIN);
        Long membership = tenant.criarMembership(tenantA.organizacaoId(), outro.getId(),
                tenantA.tokenContextual());
        tenant.concederPapel(tenantA.organizacaoId(), membership,
                tenantA.tokenContextual(), "ADMIN_ORGANIZACAO");
        String tokenOutro = tenant.selecionarOrganizacao(tenantA.organizacaoId(),
                tenant.autenticar(login, "senha123"));
        String payload = ajuste(produtoA, localA, "1", "Ajuste por ator");

        String primeiro = mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "chave-compartilhada")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String segundo = mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tokenOutro)
                        .header("Idempotency-Key", "chave-compartilhada")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(primeiro, "$.id").longValue())
                .isNotEqualTo(JsonPath.<Number>read(segundo, "$.id").longValue());
        assertThat(jdbc.queryForObject("""
                SELECT quantidade FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, BigDecimal.class, tenantA.organizacaoId(), produtoA, localA))
                .isEqualByComparingTo("2");
    }

    @Test
    void recusaSaldoNegativoCrossTenantEParentInativo() throws Exception {
        mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "negativo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste(produtoA, localA, "-1", "Baixa indevida")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "cross")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste(produtoB, localA, "1", "Cross tenant")))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE local_estoque SET ativo=false WHERE id=?", localA);
        mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "inativo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste(produtoA, localA, "1", "Local inativo")))
                .andExpect(status().isNotFound());
    }

    @Test
    void compensacaoEUnicaEInativacaoExigeSaldoZero() throws Exception {
        long original = ajustar("comp-base", "2");
        mvc.perform(delete("/api/v1/stock-locations/{id}", localA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/stock-movements/{id}/compensation", original)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "comp-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Corrigir contagem\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("COMPENSACAO"))
                .andExpect(jsonPath("$.delta").value(-2))
                .andExpect(jsonPath("$.compensadoId").value(original));
        mvc.perform(post("/api/v1/stock-movements/{id}/compensation", original)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "comp-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Duplicada\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/stock-locations/{id}", localA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    void produtoComSaldoNaoZeroNaoPodeSerInativadoPelaApi() throws Exception {
        ajustar("produto-ativo", "1");
        mvc.perform(delete("/api/produtos/{id}", produtoA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isConflict());
    }

    @Test
    void filtraRazaoEPublicaLimiarSomenteNoCruzamento() throws Exception {
        long primeiro = ajustar("limiar-entrada", "2");
        long segundo = ajustar("limiar-saida", "-1");
        String agora = OffsetDateTime.now().plusMinutes(1).toString();
        mvc.perform(get("/api/v1/stock-movements")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString())
                        .param("localEstoqueId", localA.toString())
                        .param("tipo", "AJUSTE")
                        .param("de", OffsetDateTime.now().minusMinutes(5).toString())
                        .param("ate", agora))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(segundo))
                .andExpect(jsonPath("$.content[1].id").value(primeiro));
        mvc.perform(get("/api/v1/stock-movements")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .param("de", agora).param("ate", OffsetDateTime.now().toString()))
                .andExpect(status().isBadRequest());
        Integer alertas = jdbc.queryForObject("""
                SELECT count(*) FROM evento_outbox
                WHERE organizacao_id=? AND event_type='EstoqueMinimoAtingido'
                """, Integer.class, tenantA.organizacaoId());
        assertThat(alertas).isEqualTo(1);
        String limiar = jdbc.queryForObject("""
                SELECT payload::text FROM evento_outbox
                WHERE organizacao_id=? AND event_type='EstoqueMinimoAtingido'
                """, String.class, tenantA.organizacaoId());
        Map<String, Object> payloadLimiar = JsonPath.read(limiar, "$");
        assertThat(((Number) payloadLimiar.get("produtoId")).longValue()).isEqualTo(produtoA);
        assertThat(new BigDecimal(payloadLimiar.get("saldoTotal").toString()))
                .isEqualByComparingTo("1");
        assertThat(new BigDecimal(payloadLimiar.get("quantidadeMinima").toString()))
                .isEqualByComparingTo("1");
    }

    private Long criarLocal(TenantFixture fixture, String nome) throws Exception {
        String body = mvc.perform(post("/api/v1/stock-locations")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long ajustar(String chave, String delta) throws Exception {
        String body = mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste(produtoA, localA, delta, "Ajuste " + chave)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String ajuste(Long produto, Long local, String delta, String motivo) {
        return "{\"produtoId\":" + produto + ",\"localEstoqueId\":" + local
                + ",\"delta\":" + delta + ",\"motivo\":\"" + motivo + "\"}";
    }

    private Long criarProduto(Long org, String sufixo) {
        Marca marca = new Marca();
        marca.setOrganizacaoId(org);
        marca.setNome("Marca Estoque " + sufixo);
        marca = marcas.save(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(org);
        unidade.setNome("Unidade Estoque " + sufixo);
        unidade.setSigla("ES" + sufixo);
        unidade = unidades.save(unidade);
        Produto produto = new Produto();
        produto.setOrganizacaoId(org);
        produto.setNome("Produto Estoque " + sufixo);
        produto.setMarca(marca);
        produto.setUnidadeMedida(unidade);
        produto.setQuantidadeMinima(BigDecimal.ONE);
        return produtos.saveAndFlush(produto).getId();
    }
}
