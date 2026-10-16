package com.kaneko.estacionamento.estoque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.conveniencia.Marca;
import com.kaneko.estacionamento.conveniencia.MarcaRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.conveniencia.UnidadeMedida;
import com.kaneko.estacionamento.conveniencia.UnidadeMedidaRepository;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Import(TenantTestSupport.class)
class EstoqueConcorrenciaTest {

    @Autowired private MockMvc mvc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private MarcaRepository marcas;
    @Autowired private UnidadeMedidaRepository unidades;
    @Autowired private ProdutoRepository produtos;
    @Autowired private JdbcTemplate jdbc;

    private TenantFixture fixture;
    private Long produtoId;
    private Long localId;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Marca marca = new Marca();
        marca.setOrganizacaoId(fixture.organizacaoId());
        marca.setNome("Marca concorrencia estoque " + sufixo);
        marca = marcas.save(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(fixture.organizacaoId());
        unidade.setNome("Unidade concorrencia estoque " + sufixo);
        unidade.setSigla("E" + sufixo.substring(0, 5));
        unidade = unidades.save(unidade);
        Produto produto = new Produto();
        produto.setOrganizacaoId(fixture.organizacaoId());
        produto.setNome("Produto concorrencia estoque " + sufixo);
        produto.setMarca(marca);
        produto.setUnidadeMedida(unidade);
        produtoId = produtos.saveAndFlush(produto).getId();
        String local = mvc.perform(post("/api/v1/stock-locations")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Local concorrencia " + sufixo + "\"}"))
                .andReturn().getResponse().getContentAsString();
        localId = ((Number) JsonPath.read(local, "$.id")).longValue();
    }

    @Test
    void duasBaixasPelaUltimaUnidadeGeramUmSucessoEUmConflito() throws Exception {
        postAjuste("entrada-unidade", "1");
        List<Integer> status = executarConcorrente("baixa-a", "baixa-b", "-1");
        assertThat(status).containsExactlyInAnyOrder(201, 409);
        assertThat(saldo()).isEqualByComparingTo("0");
        assertThat(totalMovimentos()).isEqualTo(2);
    }

    @Test
    void primeiraMovimentacaoConcorrenteCriaUmCarrierESomaAsEntradas() throws Exception {
        List<Integer> status = executarConcorrente("entrada-a", "entrada-b", "1");
        assertThat(status).containsExactlyInAnyOrder(201, 201);
        assertThat(saldo()).isEqualByComparingTo("2");
        assertThat(totalSaldos()).isEqualTo(1);
        assertThat(totalMovimentos()).isEqualTo(2);
    }

    @Test
    void ajusteConcorrenteComInativacaoNuncaDeixaSaldoEmLocalInativo() throws Exception {
        List<Integer> status = executarConcorrente(
                () -> postAjuste("ajuste-versus-inativacao", "1"),
                () -> mvc.perform(delete("/api/v1/stock-locations/{id}", localId)
                                .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                                .header(HttpHeaders.IF_MATCH, "\"0\""))
                        .andReturn().getResponse().getStatus());
        assertThat(status).isIn(List.of(201, 409), List.of(404, 204));
        Boolean ativo = jdbc.queryForObject(
                "SELECT ativo FROM local_estoque WHERE id=?", Boolean.class, localId);
        BigDecimal quantidade = jdbc.queryForObject("""
                SELECT COALESCE(sum(quantidade),0) FROM saldo_estoque
                WHERE organizacao_id=? AND local_estoque_id=?
                """, BigDecimal.class, fixture.organizacaoId(), localId);
        assertThat(Boolean.FALSE.equals(ativo) && quantidade.signum() != 0).isFalse();
    }

    @Test
    void doisPutsComMesmoEtagProduzemSucessoEPreconditionFailed() throws Exception {
        List<Integer> status = executarConcorrente(
                () -> putLocal("Local atualizado A"),
                () -> putLocal("Local atualizado B"));
        assertThat(status).containsExactlyInAnyOrder(200, 412);
    }

    private List<Integer> executarConcorrente(String chaveA, String chaveB, String delta)
            throws Exception {
        return executarConcorrente(
                () -> postAjuste(chaveA, delta), () -> postAjuste(chaveB, delta));
    }

    private List<Integer> executarConcorrente(Callable<Integer> operacaoA,
            Callable<Integer> operacaoB) throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = executor.submit(() -> executarApos(inicio, operacaoA));
            Future<Integer> b = executor.submit(() -> executarApos(inicio, operacaoB));
            inicio.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private int executarApos(CountDownLatch inicio, Callable<Integer> operacao) throws Exception {
        inicio.await(5, TimeUnit.SECONDS);
        return operacao.call();
    }

    private int putLocal(String nome) throws Exception {
        return mvc.perform(put("/api/v1/stock-locations/{id}", localId)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int postAjuste(String chave, String delta) throws Exception {
        return mvc.perform(post("/api/v1/stock-adjustments")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":" + produtoId + ",\"localEstoqueId\":"
                                + localId + ",\"delta\":" + delta
                                + ",\"motivo\":\"Concorrencia\"}"))
                .andReturn().getResponse().getStatus();
    }

    private BigDecimal saldo() {
        return jdbc.queryForObject("""
                SELECT quantidade FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, BigDecimal.class, fixture.organizacaoId(), produtoId, localId);
    }

    private int totalSaldos() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM saldo_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, Integer.class, fixture.organizacaoId(), produtoId, localId);
    }

    private int totalMovimentos() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM movimento_estoque
                WHERE organizacao_id=? AND produto_id=? AND local_estoque_id=?
                """, Integer.class, fixture.organizacaoId(), produtoId, localId);
    }
}
