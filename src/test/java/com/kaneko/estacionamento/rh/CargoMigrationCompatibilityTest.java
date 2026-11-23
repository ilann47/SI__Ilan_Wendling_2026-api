package com.kaneko.estacionamento.rh;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CargoMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    private String schema;
    private Long cargoId;
    private Long funcionarioId;

    @BeforeAll
    void prepararV42() {
        schema = "it_cargo_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("42").migrate();
        cargoId = jdbc.queryForObject("""
                INSERT INTO %s.cargo
                    (nome, salario_base, requer_cnh, ativo, created_at, updated_at)
                VALUES ('Cargo historico', 0, false, true, now(), now()) RETURNING id
                """.formatted(schema), Long.class);
        funcionarioId = jdbc.queryForObject("""
                INSERT INTO %s.funcionario
                    (nome, tipo, cpf, cargo_id, data_admissao, ativo, created_at, updated_at)
                VALUES ('Funcionario historico', 'FISICA', '12345678901', ?, CURRENT_DATE,
                        true, now(), now()) RETURNING id
                """.formatted(schema), Long.class, cargoId);
    }

    @AfterAll
    void limpar() {
        if (schema != null && schema.matches("it_cargo_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV42ParaV43PreservandoCargoEFuncionarioHistoricos() {
        flywayAte("43").migrate();
        assertThat(jdbc.queryForObject(
                "SELECT organizacao_id FROM " + schema + ".cargo WHERE id = ?",
                Long.class, cargoId)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT cargo_id FROM " + schema + ".funcionario WHERE id = ?",
                Long.class, funcionarioId)).isEqualTo(cargoId);
    }

    private Flyway flywayAte(String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
