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
class FuncionarioTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private FuncionarioRepository funcionarios;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long cargoA;
    private Long cargoB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        cargoA = inserirCargo(tenantA.organizacaoId(), "Cargo constraint A");
        cargoB = inserirCargo(tenantB.organizacaoId(), "Cargo constraint B");
    }

    @Test
    void cpfNormalizadoEUnicoSomenteNoTenant() {
        inserirFuncionario(tenantA.organizacaoId(), "123.456.789-01", cargoA);
        inserirFuncionario(tenantB.organizacaoId(), "12345678901", cargoB);
        assertViolation(
                () -> inserirFuncionario(tenantA.organizacaoId(), "123 456 789 01", cargoA),
                "23505", "uq_funcionario_organizacao_cpf");
    }

    @Test
    void preservaUnicidadeNormalizadaDoHistorico() {
        Long cargoHistorico = inserirCargo(null, "Cargo historico constraint");
        inserirFuncionario(null, "987.654.321-00", cargoHistorico);
        assertViolation(
                () -> inserirFuncionario(null, "98765432100", cargoHistorico),
                "23505", "uq_funcionario_legado_cpf");
    }

    @Test
    void recusaCargoDeOutroTenant() {
        assertViolation(
                () -> inserirFuncionario(tenantA.organizacaoId(), "11111111111", cargoB),
                "23503", "fk_funcionario_cargo_organizacao");
    }

    @Test
    void recusaCargoTenantEmFuncionarioHistorico() {
        assertViolation(
                () -> inserirFuncionario(null, "22222222222", cargoA),
                "23503", "fk_funcionario_cargo_organizacao");
    }

    @Test
    void usuarioGlobalNaoPodeReferenciarFuncionarioTenant() {
        Long funcionarioId = inserirFuncionario(
                tenantA.organizacaoId(), "33333333333", cargoA);
        assertViolation(() -> jdbc.update("""
                INSERT INTO usuario
                    (nome, login, senha, funcionario_id, perfil, ativo, created_at, updated_at)
                VALUES ('Usuario', 'usuario_constraint', 'hash', ?, 'OPERADOR',
                        true, now(), now())
                """, funcionarioId), "23503", "fk_usuario_funcionario_owner");
    }

    @Test
    void possuiConstraintsEstruturaisNomeadas() {
        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE conrelid = 'funcionario'::regclass
                  AND conname IN ('fk_funcionario_organizacao',
                                  'uq_funcionario_organizacao_id',
                                  'fk_funcionario_cargo_organizacao')
                """, Integer.class);
        assertThat(total).isEqualTo(3);
    }

    @Test
    void consultaDeCpfUsaMesmaNormalizacaoDoIndice() {
        inserirFuncionario(tenantA.organizacaoId(), "123.456.789-09", cargoA);
        assertThat(funcionarios.existeCpf(
                tenantA.organizacaoId(), "12345678909", null)).isTrue();
    }

    @Test
    void ownerDoFuncionarioNaoPodeSerMovidoEntreTenants() {
        Long funcionarioId = inserirFuncionario(
                tenantA.organizacaoId(), "44444444444", cargoA);
        assertViolation(() -> jdbc.update("""
                UPDATE funcionario SET organizacao_id = ?, cargo_id = ? WHERE id = ?
                """, tenantB.organizacaoId(), cargoB, funcionarioId),
                "23514", "ck_funcionario_owner_imutavel");
    }

    @Test
    void ownerDoFuncionarioNaoPodeSerRemovido() {
        Long funcionarioId = inserirFuncionario(
                tenantA.organizacaoId(), "55555555555", cargoA);
        assertViolation(() -> jdbc.update("""
                UPDATE funcionario SET organizacao_id = NULL WHERE id = ?
                """, funcionarioId), "23514", "ck_funcionario_owner_imutavel");
    }

    @Test
    void funcionarioHistoricoExigeBackfillControlado() {
        Long cargoHistorico = inserirCargo(null, "Cargo para backfill");
        Long funcionarioId = inserirFuncionario(null, "66666666666", cargoHistorico);
        assertViolation(() -> jdbc.update("""
                UPDATE funcionario SET organizacao_id = ?, cargo_id = ? WHERE id = ?
                """, tenantA.organizacaoId(), cargoA, funcionarioId),
                "23514", "ck_funcionario_owner_imutavel");
    }

    @Test
    void cargoHistoricoExigeBackfillControlado() {
        Long cargoHistorico = inserirCargo(null, "Cargo historico referenciado");
        inserirFuncionario(null, "77777777777", cargoHistorico);
        assertViolation(() -> jdbc.update("""
                UPDATE cargo SET organizacao_id = ? WHERE id = ?
                """, tenantA.organizacaoId(), cargoHistorico),
                "23514", "ck_cargo_owner_imutavel");
    }

    private Long inserirCargo(Long organizacaoId, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO cargo
                    (organizacao_id, nome, salario_base, requer_cnh, ativo,
                     created_at, updated_at)
                VALUES (?, ?, 0, false, true, now(), now()) RETURNING id
                """, Long.class, organizacaoId, nome);
    }

    private Long inserirFuncionario(Long organizacaoId, String cpf, Long cargoId) {
        return jdbc.queryForObject("""
                INSERT INTO funcionario
                    (organizacao_id, nome, tipo, cpf, cargo_id, data_admissao,
                     ativo, created_at, updated_at)
                VALUES (?, 'Funcionario', 'FISICA', ?, ?, CURRENT_DATE,
                        true, now(), now()) RETURNING id
                """, Long.class, organizacaoId, cpf, cargoId);
    }
}
