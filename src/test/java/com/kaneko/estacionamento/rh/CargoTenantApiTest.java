package com.kaneko.estacionamento.rh;

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
class CargoTenantApiTest {

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
        mvc.perform(get("/api/cargos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cargos").header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());

        String login = "cargo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/cargos").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/cargos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Negado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteMesmoNomeEmTenantsESegregaLeituraEMutacoes() throws Exception {
        long idA = criar(tenantA, "Operador");
        long idB = criar(tenantB, " operador ");

        mvc.perform(get("/api/cargos/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/cargos").header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idA));
        mvc.perform(put("/api/cargos/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Invasao\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/cargos/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/cargos/{id}", idB)
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("operador"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void rejeitaOrganizacaoECampoDesconhecidoNoPayload() throws Exception {
        mvc.perform(post("/api/cargos").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hostil\",\"organizationId\":"
                                + tenantB.organizacaoId() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cargos").header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hostil\",\"campoInventado\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preservaHistoricoSemOwnerInvisivel() throws Exception {
        Long id = jdbc.queryForObject("""
                INSERT INTO cargo
                    (nome, salario_base, requer_cnh, ativo, created_at, updated_at)
                VALUES ('Historico', 0, false, true, now(), now()) RETURNING id
                """, Long.class);
        mvc.perform(get("/api/cargos").header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/cargos/{id}", id)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void funcionarioGlobalNaoReferenciaCargoTenant() throws Exception {
        long cargoId = criar(tenantA, "Cargo tenant");
        mvc.perform(post("/api/funcionarios")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Funcionario\",\"cpf\":\"12345678901\","
                                + "\"cargoId\":" + cargoId + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditaCriacaoAtualizacaoEInativacaoComClasseRh() throws Exception {
        long id = criar(tenantA, "Auditado");
        mvc.perform(put("/api/cargos/{id}", id)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Atualizado\",\"salarioBase\":2500}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/cargos/{id}", id)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNoContent());

        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id = ? AND ator_id = ? AND recurso_id = ?
                  AND classe_retencao = 'RH'
                  AND acao IN ('CARGO_CRIADO', 'CARGO_ATUALIZADO', 'CARGO_DESATIVADO')
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), id);
        assertThat(total).isEqualTo(3);
    }

    private long criar(TenantFixture fixture, String nome) throws Exception {
        String body = mvc.perform(post("/api/cargos")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"salarioBase\":2000}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
