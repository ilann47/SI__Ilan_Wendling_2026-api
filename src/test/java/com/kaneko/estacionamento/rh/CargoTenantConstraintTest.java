package com.kaneko.estacionamento.rh;

import static com.kaneko.estacionamento.support.database.PostgresConstraintAssertions.assertViolation;
import static org.assertj.core.api.Assertions.assertThat;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TenantApiIntegrationTest
class CargoTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private CargoRepository cargos;

    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
    }

    @Test
    void nomeNormalizadoEUnicoSomenteNoTenant() {
        inserir(tenantA.organizacaoId(), "Operador");
        inserir(tenantB.organizacaoId(), " operador ");
        assertViolation(() -> inserir(tenantA.organizacaoId(), " OPERADOR "),
                "23505", "uq_cargo_organizacao_nome");
    }

    @Test
    void bancoRecusaOrganizacaoInexistente() {
        assertViolation(() -> inserir(Long.MAX_VALUE, "Invalido"),
                "23503", "fk_cargo_organizacao");
    }

    @Test
    void preservaHistoricoEUnicidadeSeparada() {
        Long tenantId = inserir(tenantA.organizacaoId(), "Historico");
        Long legadoId = inserir(null, "Historico");
        assertThat(cargos.findByIdAndOrganizacaoIdIsNull(tenantId)).isEmpty();
        assertThat(cargos.findByIdAndOrganizacaoIdIsNull(legadoId)).isPresent();
        assertViolation(() -> inserir(null, "Historico"),
                "23505", "uq_cargo_legado_nome");
    }

    @Test
    void bancoRecusaFuncionarioGlobalComCargoTenant() {
        Long cargoId = inserir(tenantA.organizacaoId(), "Cargo tenant");
        assertViolation(() -> jdbc.update("""
                INSERT INTO funcionario
                    (nome, tipo, cpf, cargo_id, data_admissao, ativo, created_at, updated_at)
                VALUES ('Funcionario', 'FISICA', '12345678901', ?, CURRENT_DATE,
                        true, now(), now())
                """, cargoId), "23503", "fk_funcionario_cargo_owner");
    }

    private Long inserir(Long organizacaoId, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO cargo
                    (organizacao_id, nome, salario_base, requer_cnh, ativo, created_at, updated_at)
                VALUES (?, ?, 0, false, true, now(), now()) RETURNING id
                """, Long.class, organizacaoId, nome);
    }
}
