package com.kaneko.estacionamento.conveniencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class ProdutoFornecedorConcorrenciaTest {

    @Autowired private MockMvc mvc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private MarcaRepository marcas;
    @Autowired private UnidadeMedidaRepository unidades;
    @Autowired private ProdutoRepository produtos;
    @Autowired private FornecedorRepository fornecedores;
    @Autowired private JdbcTemplate jdbc;

    private TenantFixture fixture;
    private Long produtoId;
    private Long fornecedorId;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Marca marca = new Marca();
        marca.setOrganizacaoId(fixture.organizacaoId());
        marca.setNome("Marca concorrencia " + sufixo);
        marca = marcas.save(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(fixture.organizacaoId());
        unidade.setNome("Unidade concorrencia " + sufixo);
        unidade.setSigla("C" + sufixo.substring(0, 5));
        unidade = unidades.save(unidade);
        Produto produto = new Produto();
        produto.setOrganizacaoId(fixture.organizacaoId());
        produto.setNome("Produto concorrencia " + sufixo);
        produto.setMarca(marca);
        produto.setUnidadeMedida(unidade);
        produtoId = produtos.save(produto).getId();
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setOrganizacaoId(fixture.organizacaoId());
        fornecedor.setNome("Fornecedor concorrencia " + sufixo);
        fornecedor.setDocumento("55" + Math.abs(UUID.randomUUID().hashCode()));
        fornecedorId = fornecedores.save(fornecedor).getId();
    }

    @Test
    void criacoesConcorrentesMantemUmVinculoERetornam201E409() throws Exception {
        List<Integer> status = executarPostsConcorrentes();
        assertThat(status).containsExactlyInAnyOrder(201, 409);
        assertThat(totalVinculos()).isEqualTo(1);
    }

    @Test
    void reativacoesConcorrentesMantemUmVinculoEPerdedorRecebe409() throws Exception {
        String criado = mvc.perform(post("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(criado, "$.id")).longValue();
        mvc.perform(delete("/api/produto-fornecedores/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                .header(HttpHeaders.IF_MATCH, "\"0\"")).andReturn();

        List<Integer> status = executarPostsConcorrentes();
        assertThat(status).containsExactlyInAnyOrder(201, 409);
        assertThat(totalVinculos()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT ativo FROM produto_fornecedor WHERE id=?",
                Boolean.class, id)).isTrue();
    }

    private List<Integer> executarPostsConcorrentes() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> primeiro = executor.submit(() -> postApos(inicio));
            Future<Integer> segundo = executor.submit(() -> postApos(inicio));
            inicio.countDown();
            return List.of(primeiro.get(30, TimeUnit.SECONDS),
                    segundo.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private int postApos(CountDownLatch inicio) throws Exception {
        inicio.await(5, TimeUnit.SECONDS);
        return mvc.perform(post("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andReturn().getResponse().getStatus();
    }

    private String payload() {
        return "{\"produtoId\":" + produtoId + ",\"fornecedorId\":" + fornecedorId
                + ",\"codigoProd\":\"CONC\",\"custo\":5.00}";
    }

    private int totalVinculos() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM produto_fornecedor
                WHERE organizacao_id=? AND produto_id=? AND fornecedor_id=?
                """, Integer.class, fixture.organizacaoId(), produtoId, fornecedorId);
    }

}
