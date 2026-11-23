package com.kaneko.estacionamento.logistica;

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

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransportadoraMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    private String schema;
    private Long transportadoraId;
    private Long condicaoId;

    @BeforeAll
    void prepararV40() {
        schema = "it_transportadora_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("40").migrate();
        condicaoId = jdbc.queryForObject("""
                INSERT INTO %s.condicao_pagamento
                    (nome, numero_parcelas, percentual_juros, percentual_multa,
                     percentual_desconto, ativo, created_at, updated_at)
                VALUES ('Condicao historica', 1, 0, 0, 0, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class);
        transportadoraId = jdbc.queryForObject("""
                INSERT INTO %s.transportadora
                    (nome, tipo, documento, condicao_pagamento_id, ativo, created_at, updated_at)
                VALUES ('Transportadora historica', 'JURIDICA', '12345678000190', ?, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class, condicaoId);
    }

    @AfterAll
    void limpar() {
        if (schema != null && schema.matches("it_transportadora_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV40ParaV41SemAtribuirTenant() {
        flywayAte("41").migrate();
        var linha = jdbc.queryForMap("""
                SELECT id, organizacao_id, condicao_pagamento_id
                FROM %s.transportadora WHERE id = ?
                """.formatted(schema), transportadoraId);
        assertThat(linha.get("id")).isEqualTo(transportadoraId);
        assertThat(linha.get("organizacao_id")).isNull();
        assertThat(linha.get("condicao_pagamento_id")).isEqualTo(condicaoId);
    }

    private Flyway flywayAte(String versao) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao))
                .load();
    }
}
