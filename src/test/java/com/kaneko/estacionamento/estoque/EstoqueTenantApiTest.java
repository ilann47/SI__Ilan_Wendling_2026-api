package com.kaneko.estacionamento.estoque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
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
    @Autowired private EstoqueService estoqueService;

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

    @Test void documentoOrigemECarregadoPorVinculoRealNoMesmoTenant() throws Exception {
        Long fornecedor = jdbc.queryForObject("INSERT INTO fornecedor(organizacao_id,nome,documento) VALUES (?,'Fornecedor do estoque',?) RETURNING id",
                Long.class, tenantA.organizacaoId(), UUID.randomUUID().toString().substring(0,14));
        Long nota = jdbc.queryForObject("INSERT INTO nota_entrada(organizacao_id,fornecedor_id,numero,local_estoque_id) VALUES (?,?,'NF-est-01',?) RETURNING id",
                Long.class, tenantA.organizacaoId(), fornecedor, localA);
        Long movimento = estoqueService.registrarRecebimento(tenantA.organizacaoId(),tenantA.usuarioId(),produtoA,localA,
                BigDecimal.ONE,BigDecimal.ONE,"NOTA_ENTRADA",nota + ":1","Recebimento documentado");
        mvc.perform(get("/api/v1/stock-movements/{id}/origin",movimento).header(HttpHeaders.AUTHORIZATION,tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(nota))
                .andExpect(jsonPath("$.descricao").value("Nota de entrada NF-est-01"))
                .andExpect(jsonPath("$.caminho").value("/app/notas-entrada/" + nota));
    }

    @Test
    void recebimentosCalculamCustoMedioPonderadoEGuardamMemoria() {
        estoqueService.registrarRecebimento(tenantA.organizacaoId(), tenantA.usuarioId(),
                produtoA, localA, new BigDecimal("10"), new BigDecimal("20.00"),
                "NOTA_ENTRADA", "media:1", "Primeira entrada");

        var resultado = estoqueService.registrarRecebimentoComCustoMedio(
                tenantA.organizacaoId(), tenantA.usuarioId(), produtoA, localA,
                new BigDecimal("5"), new BigDecimal("30.00"),
                "NOTA_ENTRADA", "media:2", "Segunda entrada");

        assertThat(resultado.saldoTotalAnterior()).isEqualByComparingTo("10");
        assertThat(resultado.saldoTotalPosterior()).isEqualByComparingTo("15");
        assertThat(resultado.custoMedioAnterior()).isEqualByComparingTo("20.00");
        assertThat(resultado.custoMedioPosterior()).isEqualByComparingTo("23.333333");
        assertThat(jdbc.queryForObject("SELECT custo_micros * 0.000001 FROM produto WHERE id=?",
                BigDecimal.class, produtoA)).isEqualByComparingTo("23.333333");
        assertThat(jdbc.queryForObject("""
                SELECT custo_medio_posterior_micros * 0.000001 FROM movimento_estoque WHERE id=?
                """, BigDecimal.class, resultado.movimentoId())).isEqualByComparingTo("23.333333");
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
    void nemAdministradorPodeAjustarOuCompensarSaldoSemDocumento() throws Exception {
        long movimento = estoqueService.registrarRecebimento(tenantA.organizacaoId(), tenantA.usuarioId(),
                produtoA, localA, BigDecimal.ONE, BigDecimal.TEN, "NOTA_ENTRADA", "1:1", "Estoque de teste");
        mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "nao-ajustar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ajuste(produtoA, localA, "2", "Não deve alterar saldo")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/stock-movements/{id}/compensation", movimento)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header("Idempotency-Key", "nao-compensar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Não deve alterar saldo\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/stock-balances").header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].quantidade").value(1));
        mvc.perform(get("/api/v1/stock-movements").header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void recebimentoFiscalExibeNomesPosicaoEAuditoria() throws Exception {
        long movimento = estoqueService.registrarRecebimento(tenantA.organizacaoId(), tenantA.usuarioId(),
                produtoA, localA, new BigDecimal("2.500"), new BigDecimal("12.30"),
                "NOTA_ENTRADA", "42:1", "Entrada conferida");
        mvc.perform(get("/api/v1/stock-movements/{id}", movimento)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.produto").value("Produto Estoque A"))
                .andExpect(jsonPath("$.localEstoque").value("Estoque principal"))
                .andExpect(jsonPath("$.delta").value(2.5)).andExpect(jsonPath("$.atorNome").isNotEmpty());
        mvc.perform(get("/api/v1/stock-movements/{id}/origin", movimento)
                        .header(HttpHeaders.AUTHORIZATION, tenantB.tokenContextual()))
                .andExpect(status().isNotFound());
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
        assertThat(persistido.getCusto()).isEqualByComparingTo("12.30");

        String evento = jdbc.queryForObject("""
                SELECT payload::text FROM evento_outbox
                WHERE aggregate_type='MOVIMENTO_ESTOQUE' AND aggregate_id=?
                  AND event_type='EstoqueRecebido'
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
                .containsEntry("tipo", "RECEBIMENTO")
                .containsEntry("origemTipo", "NOTA_ENTRADA")
                .containsEntry("motivo", "Entrada conferida")
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
    void recusaSaldoNegativoCrossTenantEParentInativo() {
        assertThatThrownBy(() -> estoqueService.registrarBaixa(tenantA.organizacaoId(), tenantA.usuarioId(),
                produtoA, localA, BigDecimal.ONE, "NOTA_SAIDA", "1:1", "Baixa fiscal"))
                .isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> receber(produtoB, "1", "2:1"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        jdbc.update("UPDATE local_estoque SET ativo=false WHERE id=?", localA);
        assertThatThrownBy(() -> receber(produtoA, "1", "3:1"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void inativacaoDeLocalExigeSaldoZero() throws Exception {
        receber(produtoA, "2", "1:1");
        mvc.perform(delete("/api/v1/stock-locations/{id}", localA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isConflict());
        estoqueService.registrarBaixa(tenantA.organizacaoId(), tenantA.usuarioId(),
                produtoA, localA, new BigDecimal("2"), "NOTA_SAIDA", "1:1", "Baixa fiscal");
        mvc.perform(delete("/api/v1/stock-locations/{id}", localA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"1\""));
    }
    @Test
    void produtoComSaldoNaoZeroNaoPodeSerInativadoPelaApi() throws Exception {
        receber(produtoA, "1", "1:1");
        String etag = mvc.perform(get("/api/produtos/{id}", produtoA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();
        mvc.perform(delete("/api/produtos/{id}", produtoA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, etag))
                .andExpect(status().isConflict());
    }

    @Test
    void filtraRazaoEPublicaLimiarSomenteNoCruzamento() throws Exception {
        receber(produtoA, "2", "1:1");
        estoqueService.registrarBaixa(tenantA.organizacaoId(), tenantA.usuarioId(),
                produtoA, localA, BigDecimal.ONE, "NOTA_SAIDA", "1:1", "Baixa fiscal");
        String agora = OffsetDateTime.now().plusMinutes(1).toString();
        mvc.perform(get("/api/v1/stock-movements")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString())
                        .param("localEstoqueId", localA.toString())
                        .param("tipo", "BAIXA")
                        .param("de", OffsetDateTime.now().minusMinutes(5).toString())
                        .param("ate", agora))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].origemTipo").value("NOTA_SAIDA"));
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

    private long receber(Long produto, String quantidade, String origem) {
        return estoqueService.registrarRecebimento(tenantA.organizacaoId(), tenantA.usuarioId(),
                produto, localA, new BigDecimal(quantidade), BigDecimal.TEN,
                "NOTA_ENTRADA", origem, "Entrada de teste");
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
