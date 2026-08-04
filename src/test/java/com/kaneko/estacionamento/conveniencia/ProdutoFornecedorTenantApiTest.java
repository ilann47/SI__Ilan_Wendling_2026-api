package com.kaneko.estacionamento.conveniencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.fornecedor.FornecedorRepository;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class ProdutoFornecedorTenantApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private MarcaRepository marcas;
    @Autowired private UnidadeMedidaRepository unidades;
    @Autowired private ProdutoRepository produtos;
    @Autowired private FornecedorRepository fornecedores;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long produtoA;
    private Long fornecedorA;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        produtoA = criarProduto(tenantA.organizacaoId(), "A");
        fornecedorA = criarFornecedor(tenantA.organizacaoId(), "A");
    }

    @Test
    void isolaTenantsHistoricoEFiltraPorProduto() throws Exception {
        Long produtoB = criarProduto(tenantB.organizacaoId(), "B");
        Long fornecedorB = criarFornecedor(tenantB.organizacaoId(), "B");
        long vinculoB = criarVinculo(tenantB, produtoB, fornecedorB, "B-1");
        long vinculoA = criarVinculo(tenantA, produtoA, fornecedorA, "A-1");
        Long outroProdutoA = criarProduto(tenantA.organizacaoId(), "A2");
        criarVinculo(tenantA, outroProdutoA, fornecedorA, "A-2");

        mvc.perform(get("/api/produto-fornecedores/{id}", vinculoB)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(produtoB, fornecedorB, "CROSS", null)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .param("produtoId", produtoA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(vinculoA));

        Long produtoHistorico = criarProduto(null, "H");
        Long fornecedorHistorico = criarFornecedor(null, "H");
        Long historico = jdbc.queryForObject("""
                INSERT INTO produto_fornecedor (produto_id,fornecedor_id)
                VALUES (?,?) RETURNING id
                """, Long.class, produtoHistorico, fornecedorHistorico);
        mvc.perform(get("/api/produto-fornecedores/{id}", historico)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void exigeAutenticacaoEPermissaoCatalogo() throws Exception {
        mvc.perform(get("/api/produto-fornecedores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());
        String login = "pf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(
                tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void payloadEFechadoEIfMatchProtegeMutacoes() throws Exception {
        long id = criarVinculo(tenantA, produtoA, fornecedorA, "PF-1");
        String payload = payload(produtoA, fornecedorA, "PF-2", null);
        mvc.perform(post("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("}", ",\"organizacaoId\":999}")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/produto-fornecedores/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(put("/api/produto-fornecedores/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "0")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/produto-fornecedores/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"9\"")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void identidadeDasPontasEImutavelNoPut() throws Exception {
        long id = criarVinculo(tenantA, produtoA, fornecedorA, "ID-1");
        Long outroProduto = criarProduto(tenantA.organizacaoId(), "ID-P");
        Long outroFornecedor = criarFornecedor(tenantA.organizacaoId(), "ID-F");
        mvc.perform(put("/api/produto-fornecedores/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(outroProduto, fornecedorA, "ID-2", null)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/produto-fornecedores/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(produtoA, outroFornecedor, "ID-3", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void exigeParentsAtivosParaCriarEReativarMasPermiteDesativar() throws Exception {
        long id = criarVinculo(tenantA, produtoA, fornecedorA, "AT-1");
        jdbc.update("UPDATE produto SET ativo=false WHERE id=?", produtoA);
        entityManager.clear();
        mvc.perform(put("/api/produto-fornecedores/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(produtoA, fornecedorA, "AT-2", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(post("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(produtoA, fornecedorA, "AT-3", true)))
                .andExpect(status().isConflict());

        Long produtoAtivo = criarProduto(tenantA.organizacaoId(), "AT-P2");
        jdbc.update("UPDATE fornecedor SET ativo=false WHERE id=?", fornecedorA);
        entityManager.clear();
        mvc.perform(post("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(produtoAtivo, fornecedorA, "AT-4", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void postReativaMesmaLinhaEAtualizaAuditoria() throws Exception {
        long id = criarVinculo(tenantA, produtoA, fornecedorA, "R-1");
        mvc.perform(delete("/api/produto-fornecedores/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"1\""));
        mvc.perform(post("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(produtoA, fornecedorA, "R-2", true)))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.codigoProd").value("R-2"));

        Integer auditorias = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id=? AND recurso_tipo='PRODUTO_FORNECEDOR'
                  AND recurso_id=? AND classe_retencao='CONFIGURACAO'
                  AND acao IN ('PRODUTO_FORNECEDOR_CRIADO',
                               'PRODUTO_FORNECEDOR_DESATIVADO',
                               'PRODUTO_FORNECEDOR_REATIVADO')
                  AND jsonb_exists(estado_posterior,'produtoId')
                  AND jsonb_exists(estado_posterior,'fornecedorId')
                  AND jsonb_exists(estado_posterior,'codigoProd')
                  AND jsonb_exists(estado_posterior,'custo')
                  AND jsonb_exists(estado_posterior,'ativo')
                """, Integer.class, tenantA.organizacaoId(), id);
        assertThat(auditorias).isEqualTo(3);
    }

    @Test
    void putReativandoUsaAcaoDeAuditoriaEspecifica() throws Exception {
        long id = criarVinculo(tenantA, produtoA, fornecedorA, "PR-1");
        mvc.perform(delete("/api/produto-fornecedores/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/produto-fornecedores/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(produtoA, fornecedorA, "PR-2", true)))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""));
        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE recurso_tipo='PRODUTO_FORNECEDOR' AND recurso_id=?
                  AND acao='PRODUTO_FORNECEDOR_REATIVADO'
                """, Integer.class, id);
        assertThat(total).isEqualTo(1);
    }

    private long criarVinculo(TenantFixture fixture, Long produto, Long fornecedor,
            String codigo) throws Exception {
        String body = mvc.perform(post("/api/produto-fornecedores")
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(produto, fornecedor, codigo, null)))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String payload(Long produto, Long fornecedor, String codigo, Boolean ativo) {
        return "{\"produtoId\":" + produto + ",\"fornecedorId\":" + fornecedor
                + ",\"codigoProd\":\"" + codigo + "\",\"custo\":3.50"
                + (ativo == null ? "" : ",\"ativo\":" + ativo) + "}";
    }

    private Long criarProduto(Long organizacaoId, String sufixo) {
        Marca marca = new Marca();
        marca.setOrganizacaoId(organizacaoId);
        marca.setNome("Marca PF " + sufixo + UUID.randomUUID().toString().substring(0, 5));
        marca = marcas.save(marca);
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(organizacaoId);
        unidade.setNome("Unidade PF " + sufixo + UUID.randomUUID().toString().substring(0, 5));
        unidade.setSigla("P" + UUID.randomUUID().toString().substring(0, 5));
        unidade = unidades.save(unidade);
        Produto produto = new Produto();
        produto.setOrganizacaoId(organizacaoId);
        produto.setNome("Produto PF " + sufixo);
        produto.setMarca(marca);
        produto.setUnidadeMedida(unidade);
        return produtos.save(produto).getId();
    }

    private Long criarFornecedor(Long organizacaoId, String sufixo) {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setOrganizacaoId(organizacaoId);
        fornecedor.setNome("Fornecedor PF " + sufixo);
        fornecedor.setDocumento("53" + Math.abs(UUID.randomUUID().hashCode()));
        return fornecedores.save(fornecedor).getId();
    }
}
