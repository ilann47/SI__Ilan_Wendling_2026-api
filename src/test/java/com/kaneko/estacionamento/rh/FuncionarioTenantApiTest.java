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
class FuncionarioTenantApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CargoRepository cargos;
    @Autowired private TenantTestSupport tenant;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long cargoA;
    private Long cargoB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        cargoA = criarCargo(tenantA.organizacaoId(), "Cargo A");
        cargoB = criarCargo(tenantB.organizacaoId(), "Cargo B");
    }

    @Test
    void exigeContextoEPermissao() throws Exception {
        mvc.perform(get("/api/funcionarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/funcionarios")
                        .header("Authorization", tenantA.tokenGlobal()))
                .andExpect(status().isForbidden());

        String login = "func_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Usuario operador = tenant.criarUsuario(login, PerfilUsuario.OPERADOR);
        tenant.criarMembership(
                tenantA.organizacaoId(), operador.getId(), tenantA.tokenContextual());
        String token = tenant.selecionarOrganizacao(
                tenantA.organizacaoId(), tenant.autenticar(login, "senha123"));
        mvc.perform(get("/api/funcionarios").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/funcionarios")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Negado", "00000000000", cargoA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteMesmoCpfEmTenantsESegregaLeituraEMutacoes() throws Exception {
        long idA = criarFuncionario(tenantA, "Funcionario A", "123.456.789-01", cargoA);
        long idB = criarFuncionario(tenantB, "Funcionario B", "12345678901", cargoB);

        mvc.perform(get("/api/funcionarios/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/funcionarios")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idA));
        mvc.perform(put("/api/funcionarios/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Invasao", "12345678901", cargoA)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/funcionarios/{id}", idB)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/funcionarios/{id}", idB)
                        .header("Authorization", tenantB.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Funcionario B"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void rejeitaCargoDeOutroTenant() throws Exception {
        mvc.perform(post("/api/funcionarios")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Invalido", "11111111111", cargoB)))
                .andExpect(status().isNotFound());

        long id = criarFuncionario(tenantA, "Valido", "22222222222", cargoA);
        mvc.perform(put("/api/funcionarios/{id}", id)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Invalido", "22222222222", cargoB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejeitaOrganizacaoECampoDesconhecidoNoPayload() throws Exception {
        mvc.perform(post("/api/funcionarios")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hostil\",\"cpf\":\"33333333333\","
                                + "\"cargoId\":" + cargoA + ",\"organizationId\":"
                                + tenantB.organizacaoId() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/funcionarios")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hostil\",\"cpf\":\"33333333333\","
                                + "\"cargoId\":" + cargoA + ",\"campoInventado\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historicoSemOwnerPermaneceInvisivel() throws Exception {
        Long cargoHistorico = inserirCargoHistorico("Cargo historico");
        Long funcionarioId = jdbc.queryForObject("""
                INSERT INTO funcionario
                    (nome, tipo, cpf, cargo_id, data_admissao, ativo, created_at, updated_at)
                VALUES ('Historico', 'FISICA', '44444444444', ?, CURRENT_DATE,
                        true, now(), now()) RETURNING id
                """, Long.class, cargoHistorico);

        mvc.perform(get("/api/funcionarios")
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/funcionarios/{id}", funcionarioId)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioGlobalNaoReferenciaFuncionarioTenant() throws Exception {
        long funcionarioId = criarFuncionario(
                tenantA, "Funcionario tenant", "55555555555", cargoA);
        mvc.perform(post("/api/usuarios")
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Usuario\",\"login\":\"usuario_func_tenant\","
                                + "\"senha\":\"senha123\",\"funcionarioId\":"
                                + funcionarioId + ",\"perfil\":\"OPERADOR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditaSemCopiarPiiParaSnapshots() throws Exception {
        long id = criarFuncionario(tenantA, "Auditado", "66666666666", cargoA);
        mvc.perform(put("/api/funcionarios/{id}", id)
                        .header("Authorization", tenantA.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Atualizado", "66666666666", cargoA)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/funcionarios/{id}", id)
                        .header("Authorization", tenantA.tokenContextual()))
                .andExpect(status().isNoContent());

        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id = ? AND ator_id = ? AND recurso_id = ?
                  AND classe_retencao = 'RH'
                  AND acao IN ('FUNCIONARIO_CRIADO', 'FUNCIONARIO_ATUALIZADO',
                               'FUNCIONARIO_DESATIVADO')
                """, Integer.class, tenantA.organizacaoId(), tenantA.usuarioId(), id);
        Integer snapshotInvalido = jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE organizacao_id = ? AND recurso_id = ?
                  AND classe_retencao = 'RH'
                  AND (
                    (estado_anterior IS NOT NULL AND (
                        estado_anterior - 'cargoId' - 'ativo' <> '{}'::jsonb
                        OR NOT jsonb_exists_all(
                            estado_anterior, ARRAY['cargoId', 'ativo'])
                    ))
                    OR
                    (estado_posterior IS NOT NULL AND (
                        estado_posterior - 'cargoId' - 'ativo' <> '{}'::jsonb
                        OR NOT jsonb_exists_all(
                            estado_posterior, ARRAY['cargoId', 'ativo'])
                    ))
                  )
                """, Integer.class, tenantA.organizacaoId(), id);
        assertThat(total).isEqualTo(3);
        assertThat(snapshotInvalido).isZero();
    }

    private long criarFuncionario(
            TenantFixture fixture, String nome, String cpf, Long cargoId) throws Exception {
        String body = mvc.perform(post("/api/funcionarios")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(nome, cpf, cargoId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long criarCargo(Long organizacaoId, String nome) {
        Cargo cargo = new Cargo();
        cargo.setOrganizacaoId(organizacaoId);
        cargo.setNome(nome);
        return cargos.saveAndFlush(cargo).getId();
    }

    private Long inserirCargoHistorico(String nome) {
        return jdbc.queryForObject("""
                INSERT INTO cargo
                    (nome, salario_base, requer_cnh, ativo, created_at, updated_at)
                VALUES (?, 0, false, true, now(), now()) RETURNING id
                """, Long.class, nome);
    }

    private static String payload(String nome, String cpf, Long cargoId) {
        return "{\"nome\":\"" + nome + "\",\"cpf\":\"" + cpf
                + "\",\"cargoId\":" + cargoId + "}";
    }
}
