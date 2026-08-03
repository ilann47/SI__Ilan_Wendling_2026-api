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
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
class ProdutoServicoTenantApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private long marcaA;
    private long unidadeA;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        marcaA = criar("/api/marcas", tenantA, "{\"nome\":\"Marca A\"}");
        unidadeA = criar("/api/unidades-medida", tenantA,
                "{\"nome\":\"Unidade A\",\"sigla\":\"UNA\"}");
    }

    @Test
    void isolaProdutosEServicosEntreTenants() throws Exception {
        long produtoA = criarProduto(tenantA, "7890001", marcaA, unidadeA);
        long marcaB = criar("/api/marcas", tenantB, "{\"nome\":\"Marca B\"}");
        long unidadeB = criar("/api/unidades-medida", tenantB,
                "{\"nome\":\"Unidade B\",\"sigla\":\"UNB\"}");
        long produtoB = criarProduto(tenantB, "7890001", marcaB, unidadeB);
        long servicoA = criar("/api/servicos", tenantA, "{\"nome\":\"Lavagem\"}");
        long servicoB = criar("/api/servicos", tenantB, "{\"nome\":\" lavagem \"}");

        assertInvisivel(tenantA, "/api/produtos", produtoB);
        assertInvisivel(tenantA, "/api/servicos", servicoB);
        mvc.perform(get("/api/produtos/{id}", produtoA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""));
        mvc.perform(get("/api/servicos/{id}", servicoA)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""));
    }

    @Test
    void exigeIfMatchERegistraAuditoriaVersionada() throws Exception {
        long produto = criarProduto(tenantA, " 7890020 ", marcaA, unidadeA);
        String payload = produtoPayload("7890021", marcaA, unidadeA);

        mvc.perform(put("/api/produtos/{id}", produto)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(put("/api/produtos/{id}", produto)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "0")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/produtos/{id}", produto)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(put("/api/produtos/{id}", produto)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(delete("/api/produtos/{id}", produto)
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"2\""));

        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id=? AND recurso_tipo='PRODUTO'
                  AND classe_retencao='CONFIGURACAO'
                  AND acao IN ('PRODUTO_CRIADO','PRODUTO_ATUALIZADO','PRODUTO_DESATIVADO')
                """, Integer.class, tenantA.organizacaoId());
        assertThat(total).isEqualTo(3);
    }

    @Test
    void fechaPayloadsRejeitaParentsDeOutroTenantEDuplicidadesNormalizadas()
            throws Exception {
        long marcaB = criar("/api/marcas", tenantB, "{\"nome\":\"Marca B\"}");
        criarProduto(tenantA, "ABC-1", marcaA, unidadeA);
        criar("/api/servicos", tenantA, "{\"nome\":\"Polimento\"}");

        mvc.perform(post("/api/produtos")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload(" ABC-1 ", marcaA, unidadeA)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/servicos")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\" polimento \"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/produtos")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("ABC-2", marcaB, unidadeA)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/servicos")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hostil\",\"organizacaoId\":999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exigeAutenticacaoEPermissoesNasRotasV52() throws Exception {
        mvc.perform(get("/api/produtos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/servicos")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());

        String login = "catalog_v52_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(
                tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));

        mvc.perform(get("/api/produtos").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/servicos").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Negado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejeitaMarcaInativa() throws Exception {
        jdbc.update("UPDATE marca SET ativo=false WHERE id=?", marcaA);
        entityManager.clear();
        mvc.perform(post("/api/produtos")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("INAT-M", marcaA, unidadeA)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejeitaUnidadeInativa() throws Exception {
        jdbc.update("UPDATE unidade_medida SET ativo=false WHERE id=?", unidadeA);
        entityManager.clear();
        mvc.perform(post("/api/produtos")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(produtoPayload("INAT-U", marcaA, unidadeA)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejeitaCategoriaInativa() throws Exception {
        long categoria = criar(
                "/api/categorias", tenantA, "{\"nome\":\"Categoria inativa\"}");
        jdbc.update("UPDATE categoria SET ativo=false WHERE id=?", categoria);
        entityManager.clear();
        String payload = produtoPayload("INAT-C", marcaA, unidadeA)
                .replace("}", ",\"categoriaId\":" + categoria + "}");
        mvc.perform(post("/api/produtos")
                        .header(HttpHeaders.AUTHORIZATION, tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void auditoriaDeProdutoRegistraSnapshotCompleto() throws Exception {
        String payload = "{\"nome\":\"Produto auditado\",\"codigoBarras\":\"AUD-52\","
                + "\"referencia\":\"REF52\",\"marcaId\":" + marcaA
                + ",\"unidadeMedidaId\":" + unidadeA
                + ",\"descricao\":\"Descricao auditada\","
                + "\"observacao\":\"Observacao auditada\"}";
        long produto = criar("/api/produtos", tenantA, payload);

        Map<String, Object> snapshot = jdbc.queryForMap("""
                SELECT estado_posterior ->> 'referencia' AS referencia,
                       estado_posterior ->> 'descricao' AS descricao,
                       estado_posterior ->> 'observacao' AS observacao,
                       estado_posterior ->> 'percentualLucro' AS percentual_lucro
                FROM registro_auditoria
                WHERE organizacao_id=? AND recurso_tipo='PRODUTO' AND recurso_id=?
                  AND acao='PRODUTO_CRIADO'
                """, tenantA.organizacaoId(), produto);
        assertThat(snapshot).containsEntry("referencia", "REF52")
                .containsEntry("descricao", "Descricao auditada")
                .containsEntry("observacao", "Observacao auditada")
                .containsEntry("percentual_lucro", "0");
    }

    private long criarProduto(TenantFixture fixture, String codigo, long marca, long unidade)
            throws Exception {
        return criar("/api/produtos", fixture, produtoPayload(codigo, marca, unidade));
    }

    private String produtoPayload(String codigo, long marca, long unidade) {
        return "{\"nome\":\"Produto " + codigo.trim() + "\",\"codigoBarras\":\""
                + codigo + "\",\"marcaId\":" + marca + ",\"unidadeMedidaId\":" + unidade
                + "}";
    }

    private long criar(String rota, TenantFixture fixture, String payload) throws Exception {
        String body = mvc.perform(post(rota)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void assertInvisivel(TenantFixture fixture, String rota, long id) throws Exception {
        mvc.perform(get(rota + "/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(rota + "/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, fixture.tokenContextual())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNotFound());
    }
}
