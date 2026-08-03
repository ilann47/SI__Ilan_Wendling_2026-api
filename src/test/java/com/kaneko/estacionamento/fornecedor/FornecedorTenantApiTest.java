package com.kaneko.estacionamento.fornecedor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class FornecedorTenantApiTest {

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
    void exigeContextoEPermissao() throws Exception {
        mvc.perform(get("/api/fornecedores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fornecedores").header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());

        String login = "supplier_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/fornecedores").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fornecedores").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Negado\",\"documento\":\"00000000000000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteDocumentoIgualEmTenantsESegregaTodasAsMutacoes() throws Exception {
        long idA = criar(tenantA, "Fornecedor A", "12.345.678/0001-90", null, null);
        long idB = criar(tenantB, "Fornecedor B", "12345678000190", null, null);

        mvc.perform(get("/api/fornecedores/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/fornecedores").header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idA));
        mvc.perform(put("/api/fornecedores/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Invasao\",\"documento\":\"12345678000190\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/fornecedores/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/fornecedores/{id}", idB)
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Fornecedor B"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void recusaCondicaoETransportadoraDeOutroTenant() throws Exception {
        long condicaoB = criarCondicao(tenantB, "Prazo B");
        long transportadoraB = criarTransportadora(tenantB, "Transportadora B");
        mvc.perform(post("/api/fornecedores").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Fornecedor A\",\"documento\":\"11111111000111\","
                                + "\"condicaoPagamentoId\":" + condicaoB + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/fornecedores").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Fornecedor A\",\"documento\":\"22222222000122\","
                                + "\"transportadoraId\":" + transportadoraB + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaTenantNoPayloadEPreservaHistoricoInvisivel() throws Exception {
        mvc.perform(post("/api/fornecedores").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hostil\",\"documento\":\"33333333000133\","
                                + "\"organizationId\":" + tenantB.organizacaoId() + "}"))
                .andExpect(status().isBadRequest());
        Long historico = jdbc.queryForObject("""
                INSERT INTO fornecedor
                    (nome, tipo, documento, limite_credito, ativo, created_at, updated_at)
                VALUES ('Historico', 'JURIDICA', '99999999000199', 0, true, now(), now())
                RETURNING id
                """, Long.class);
        mvc.perform(get("/api/fornecedores").header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/fornecedores/{id}", historico)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditaMutacoesSemExporContatosNoSnapshot() throws Exception {
        long id = criar(tenantA, "Auditado", "77777777000177", null, null);
        mvc.perform(put("/api/fornecedores/{id}", id)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa Privada\",\"documento\":\"77777777000177\","
                                + "\"endereco\":\"Rua Privada\",\"observacao\":\"Segredo\","
                                + "\"limiteCredito\":9999,"
                                + "\"emails\":[{\"email\":\"privado@teste.com\",\"principal\":true}],"
                                + "\"telefones\":[{\"telefone\":\"44999999999\",\"principal\":true}]}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/fornecedores/{id}", id)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNoContent());

        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id = ? AND ator_id = ? AND recurso_id = ?
                  AND acao IN ('FORNECEDOR_CRIADO', 'FORNECEDOR_ATUALIZADO', 'FORNECEDOR_DESATIVADO')
                  AND concat_ws(' ', estado_anterior::text, estado_posterior::text)
                      !~ '(Pessoa Privada|77777777000177|Rua Privada|Segredo|9999|privado@teste.com|44999999999)'
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), id);
        assertThat(total).isEqualTo(3);
    }

    @Test
    void rejeitaTenantEmContatoAninhado() throws Exception {
        mvc.perform(post("/api/fornecedores").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hostil\",\"documento\":\"12121212000112\","
                                + "\"emails\":[{\"email\":\"a@b.com\",\"organizationId\":"
                                + tenantB.organizacaoId() + "}]}"))
                .andExpect(status().isBadRequest());
    }

    private long criar(TenantFixture fixture, String nome, String documento,
                       Long condicaoId, Long transportadoraId) throws Exception {
        String refs = (condicaoId == null ? "" : ",\"condicaoPagamentoId\":" + condicaoId)
                + (transportadoraId == null ? "" : ",\"transportadoraId\":" + transportadoraId);
        String body = mvc.perform(post("/api/fornecedores")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"documento\":\""
                                + documento + "\"" + refs + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long criarCondicao(TenantFixture fixture, String nome) throws Exception {
        String body = mvc.perform(post("/api/condicoes-pagamento")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"numeroParcelas\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long criarTransportadora(TenantFixture fixture, String nome) throws Exception {
        String body = mvc.perform(post("/api/transportadoras")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"documento\":\"88888888000188\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
