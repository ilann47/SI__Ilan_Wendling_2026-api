package com.kaneko.estacionamento.fornecedor;

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
class FornecedorHardeningMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    private String schema;
    private Long fornecedorId;

    @BeforeAll
    void prepararV43() {
        schema = "it_supplier_hardening_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("43").migrate();
        fornecedorId = jdbc.queryForObject("""
                INSERT INTO %s.fornecedor
                    (nome, tipo, documento, limite_credito, ativo, created_at, updated_at)
                VALUES ('Historico', 'JURIDICA', '12345678000190', 0, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class);
        jdbc.update("""
                INSERT INTO %s.nota_entrada
                    (numero, fornecedor_id, created_at, updated_at)
                VALUES ('H-1', ?, now(), now())
                """.formatted(schema), fornecedorId);
    }

    @AfterAll
    void limpar() {
        if (schema != null && schema.matches("it_supplier_hardening_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV43ParaV44PreservandoGrafoHistorico() {
        flywayAte("44").migrate();
        assertThat(jdbc.queryForObject(
                "SELECT organizacao_id FROM " + schema + ".fornecedor WHERE id = ?",
                Long.class, fornecedorId)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM " + schema + ".nota_entrada WHERE fornecedor_id = ?",
                Integer.class, fornecedorId)).isEqualTo(1);
    }

    private Flyway flywayAte(String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
