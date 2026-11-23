package com.kaneko.estacionamento.rh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class FuncionarioHardeningMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    private String schema;

    @AfterEach
    void limpar() {
        if (schema != null && schema.matches("it_func_hardening_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void v46RecusaGrafoHistoricoComCargoPromovidoSemFuncionario() {
        schema = novoSchema();
        flywayAte("42").migrate();
        Long organizacaoId = inserirOrganizacao();
        Long cargoId = inserirCargoHistorico();
        inserirFuncionarioHistorico(cargoId);
        flywayAte("44").migrate();

        jdbc.update("UPDATE " + schema + ".cargo SET organizacao_id = ? WHERE id = ?",
                organizacaoId, cargoId);
        flywayAte("45").migrate();

        Throwable falha = catchThrowable(() -> flywayAte("46").migrate());
        assertThat(falha).isNotNull();
        assertThat(causaRaiz(falha).getMessage())
                .contains("fk_funcionario_cargo_organizacao");
    }

    private String novoSchema() {
        String nome = "it_func_hardening_"
                + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + nome);
        return nome;
    }

    private Long inserirOrganizacao() {
        return jdbc.queryForObject("""
                INSERT INTO %s.organizacao
                    (documento, razao_social, moeda, fuso_horario, regiao, plano,
                     status, version, created_at, updated_at)
                VALUES ('99888777000166', 'Tenant hardening', 'BRL',
                        'America/Sao_Paulo', 'BR-PR', 'ENTERPRISE', 'ATIVA', 0,
                        now(), now()) RETURNING id
                """.formatted(schema), Long.class);
    }

    private Long inserirCargoHistorico() {
        return jdbc.queryForObject("""
                INSERT INTO %s.cargo
                    (nome, salario_base, requer_cnh, ativo, created_at, updated_at)
                VALUES ('Cargo historico inconsistente', 0, false, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class);
    }

    private void inserirFuncionarioHistorico(Long cargoId) {
        jdbc.update("""
                INSERT INTO %s.funcionario
                    (nome, tipo, cpf, cargo_id, data_admissao, ativo,
                     created_at, updated_at)
                VALUES ('Funcionario historico', 'FISICA', '99888777666', ?,
                        CURRENT_DATE, true, now(), now())
                """.formatted(schema), cargoId);
    }

    private Flyway flywayAte(String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }

    private static Throwable causaRaiz(Throwable falha) {
        Throwable atual = falha;
        while (atual.getCause() != null) {
            atual = atual.getCause();
        }
        return atual;
    }
}
