package com.kaneko.estacionamento.rh;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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
class FuncionarioMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    private String schema;
    private Long cargoId;
    private Long funcionarioId;
    private Long usuarioId;

    @BeforeAll
    void prepararV44() {
        schema = "it_funcionario_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("42").migrate();
        cargoId = jdbc.queryForObject("""
                INSERT INTO %s.cargo
                    (nome, salario_base, requer_cnh, ativo, created_at, updated_at)
                VALUES ('Cargo historico funcionario', 0, false, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class);
        funcionarioId = jdbc.queryForObject("""
                INSERT INTO %s.funcionario
                    (nome, tipo, cpf, cargo_id, data_admissao, ativo,
                     created_at, updated_at)
                VALUES ('Funcionario historico', 'FISICA', '123.456.789-01', ?,
                        CURRENT_DATE, true, now(), now()) RETURNING id
                """.formatted(schema), Long.class, cargoId);
        flywayAte("44").migrate();
        usuarioId = jdbc.queryForObject("""
                INSERT INTO %s.usuario
                    (nome, login, senha, funcionario_id, perfil, ativo,
                     created_at, updated_at)
                VALUES ('Usuario historico', 'usuario_func_historico', 'hash', ?,
                        'OPERADOR', true, now(), now()) RETURNING id
                """.formatted(schema), Long.class, funcionarioId);
    }

    @AfterAll
    void limpar() {
        if (schema != null && schema.matches("it_funcionario_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV44ParaV45PreservandoCargoFuncionarioEUsuarioHistoricos() {
        flywayAte("45").migrate();

        Map<String, Object> funcionario = jdbc.queryForMap("""
                SELECT organizacao_id, cargo_id FROM %s.funcionario WHERE id = ?
                """.formatted(schema), funcionarioId);
        Long funcionarioDoUsuario = jdbc.queryForObject(
                "SELECT funcionario_id FROM " + schema + ".usuario WHERE id = ?",
                Long.class, usuarioId);
        assertThat(funcionario.get("organizacao_id")).isNull();
        assertThat(funcionario.get("cargo_id")).isEqualTo(cargoId);
        assertThat(funcionarioDoUsuario).isEqualTo(funcionarioId);
    }

    private Flyway flywayAte(String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
