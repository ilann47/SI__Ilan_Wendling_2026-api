package com.kaneko.estacionamento.conveniencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@TenantApiIntegrationTest
class CatalogoConvenienciaTenantApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;

    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
    }

    @Test
    void exigeContextoEPermissoesDeCatalogo() throws Exception {
        mvc.perform(get("/api/categorias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/marcas").header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());

        String login = "catalogo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/unidades-medida").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categorias").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Negada\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolaOsTresCadastrosEntreTenantsEPreservaContratos() throws Exception {
        long categoriaA = criar("/api/categorias", tenantA, "{\"nome\":\"Bebidas\"}");
        long categoriaB = criar("/api/categorias", tenantB, "{\"nome\":\" bebidas \"}");
        long marcaA = criar("/api/marcas", tenantA, "{\"nome\":\"Kaneko\"}");
        long marcaB = criar("/api/marcas", tenantB, "{\"nome\":\" kaneko \"}");
        long unidadeA = criar("/api/unidades-medida", tenantA,
                "{\"nome\":\"Unidade\",\"sigla\":\"un\"}");
        long unidadeB = criar("/api/unidades-medida", tenantB,
                "{\"nome\":\"Unidade\",\"sigla\":\" UN \"}");

        assertInvisivel(tenantA, "/api/categorias", categoriaB);
        assertInvisivel(tenantA, "/api/marcas", marcaB);
        assertInvisivel(tenantA, "/api/unidades-medida", unidadeB);
        assertMutacoesInvisiveis(tenantA, "/api/categorias", categoriaB,
                "{\"nome\":\"Invasao\"}");
        assertMutacoesInvisiveis(tenantA, "/api/marcas", marcaB,
                "{\"nome\":\"Invasao\"}");
        assertMutacoesInvisiveis(tenantA, "/api/unidades-medida", unidadeB,
                "{\"nome\":\"Invasao\",\"sigla\":\"INV\"}");
        mvc.perform(get("/api/categorias/{id}", categoriaA)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Bebidas"));
        mvc.perform(get("/api/marcas/{id}", marcaA)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Kaneko"));
        mvc.perform(get("/api/unidades-medida/{id}", unidadeA)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sigla").value("UN"));
    }

    @Test
    void rejeitaDuplicidadeNormalizadaNoMesmoTenant() throws Exception {
        criar("/api/categorias", tenantA, "{\"nome\":\"Bebidas\"}");
        criar("/api/marcas", tenantA, "{\"nome\":\"Kaneko\"}");
        criar("/api/unidades-medida", tenantA, "{\"nome\":\"Unidade\",\"sigla\":\"UN\"}");

        mvc.perform(post("/api/categorias").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\" bebidas \"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/marcas").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\" KANEKO \"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/unidades-medida").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Unidades\",\"sigla\":\" un \"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void payloadNaoAceitaTenantNemCampoDesconhecido() throws Exception {
        for (String rota : new String[]{"/api/categorias", "/api/marcas"}) {
            mvc.perform(post(rota).header("Authorization", tenantA.tokenContextual())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Hostil\",\"organizationId\":"
                                    + tenantB.organizacaoId() + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/unidades-medida").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Unidade\",\"sigla\":\"UN\",\"inventado\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historicoSemOwnerPermaneceInvisivel() throws Exception {
        Long categoria = jdbc.queryForObject("""
                INSERT INTO categoria (nome,ativo,created_at,updated_at)
                VALUES ('Categoria historica',true,now(),now()) RETURNING id
                """, Long.class);
        Long marca = jdbc.queryForObject("""
                INSERT INTO marca (nome,ativo,created_at,updated_at)
                VALUES ('Marca historica',true,now(),now()) RETURNING id
                """, Long.class);
        Long unidade = jdbc.queryForObject("""
                INSERT INTO unidade_medida (nome,sigla,ativo,created_at,updated_at)
                VALUES ('Unidade historica','HIS',true,now(),now()) RETURNING id
                """, Long.class);

        assertInvisivel(tenantA, "/api/categorias", categoria);
        assertInvisivel(tenantA, "/api/marcas", marca);
        assertInvisivel(tenantA, "/api/unidades-medida", unidade);
    }

    @Test
    void atualizaInativaEAuditaComRetencaoConfiguracao() throws Exception {
        long categoria = criar("/api/categorias", tenantA, "{\"nome\":\"Bebida\"}");
        long marca = criar("/api/marcas", tenantA, "{\"nome\":\"Marca\"}");
        long unidade = criar("/api/unidades-medida", tenantA,
                "{\"nome\":\"Unidade\",\"sigla\":\"UN\"}");

        atualizarEExcluir("/api/categorias", categoria, "{\"nome\":\"Bebidas\"}");
        atualizarEExcluir("/api/marcas", marca, "{\"nome\":\"Marca Nova\"}");
        atualizarEExcluir("/api/unidades-medida", unidade,
                "{\"nome\":\"Pacote\",\"sigla\":\"PCT\"}");

        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id=? AND ator_id=? AND classe_retencao='CONFIGURACAO'
                  AND acao IN ('CATEGORIA_CRIADA','CATEGORIA_ATUALIZADA','CATEGORIA_DESATIVADA',
                               'MARCA_CRIADA','MARCA_ATUALIZADA','MARCA_DESATIVADA',
                               'UNIDADE_MEDIDA_CRIADA','UNIDADE_MEDIDA_ATUALIZADA',
                               'UNIDADE_MEDIDA_DESATIVADA')
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId());
        assertThat(total).isEqualTo(9);
    }

    @Test
    void etagEIfMatchProtegemOsTresCadastros() throws Exception {
        String[][] casos = {
                {"/api/categorias", "{\"nome\":\"Categoria V\"}",
                        "{\"nome\":\"Categoria V2\"}"},
                {"/api/marcas", "{\"nome\":\"Marca V\"}",
                        "{\"nome\":\"Marca V2\"}"},
                {"/api/unidades-medida", "{\"nome\":\"Caixa V\",\"sigla\":\"CXV\"}",
                        "{\"nome\":\"Pacote V\",\"sigla\":\"PCV\"}"}
        };
        for (String[] caso : casos) {
            String body = mvc.perform(post(caso[0]).header("Authorization", tenantA.tokenContextual())
                            .contentType(MediaType.APPLICATION_JSON).content(caso[1]))
                    .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                    .andExpect(jsonPath("$.version").value(0))
                    .andReturn().getResponse().getContentAsString();
            long id = ((Number) JsonPath.read(body, "$.id")).longValue();
            mvc.perform(get(caso[0] + "/{id}", id)
                            .header("Authorization", tenantA.tokenContextual()))
                    .andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""));
            mvc.perform(put(caso[0] + "/{id}", id)
                            .header("Authorization", tenantA.tokenContextual())
                            .contentType(MediaType.APPLICATION_JSON).content(caso[2]))
                    .andExpect(status().isPreconditionRequired());
            mvc.perform(put(caso[0] + "/{id}", id)
                            .header("Authorization", tenantA.tokenContextual())
                            .header("If-Match", "0")
                            .contentType(MediaType.APPLICATION_JSON).content(caso[2]))
                    .andExpect(status().isBadRequest());
            mvc.perform(put(caso[0] + "/{id}", id)
                            .header("Authorization", tenantA.tokenContextual())
                            .header("If-Match", "\"99\"")
                            .contentType(MediaType.APPLICATION_JSON).content(caso[2]))
                    .andExpect(status().isPreconditionFailed());
            mvc.perform(put(caso[0] + "/{id}", id)
                            .header("Authorization", tenantA.tokenContextual())
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON).content(caso[2]))
                    .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                    .andExpect(jsonPath("$.version").value(1));
            mvc.perform(delete(caso[0] + "/{id}", id)
                            .header("Authorization", tenantA.tokenContextual()))
                    .andExpect(status().isPreconditionRequired());
            mvc.perform(delete(caso[0] + "/{id}", id)
                            .header("Authorization", tenantA.tokenContextual())
                            .header("If-Match", "\"0\""))
                    .andExpect(status().isPreconditionFailed());
            mvc.perform(delete(caso[0] + "/{id}", id)
                            .header("Authorization", tenantA.tokenContextual())
                            .header("If-Match", "\"1\""))
                    .andExpect(status().isNoContent()).andExpect(header().string("ETag", "\"2\""));
        }
    }

    private long criar(String rota, TenantFixture fixture, String payload) throws Exception {
        String body = mvc.perform(post(rota).header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void assertInvisivel(TenantFixture fixture, String rota, long id) throws Exception {
        mvc.perform(get(rota + "/{id}", id).header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    private void assertMutacoesInvisiveis(
            TenantFixture fixture, String rota, long id, String payload) throws Exception {
        mvc.perform(put(rota + "/{id}", id).header("Authorization", fixture.tokenContextual())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNotFound());
        mvc.perform(delete(rota + "/{id}", id).header("Authorization", fixture.tokenContextual())
                        .header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    private void atualizarEExcluir(String rota, long id, String payload) throws Exception {
        mvc.perform(put(rota + "/{id}", id).header("Authorization", tenantA.tokenContextual())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        mvc.perform(delete(rota + "/{id}", id).header("Authorization", tenantA.tokenContextual())
                        .header("If-Match", "\"1\""))
                .andExpect(status().isNoContent());
    }
}
