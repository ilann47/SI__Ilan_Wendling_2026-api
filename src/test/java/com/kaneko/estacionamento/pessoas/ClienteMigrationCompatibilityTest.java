package com.kaneko.estacionamento.pessoas;

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
class ClienteMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    private String schema;
    private Long clienteId;
    private Long condicaoId;

    @BeforeAll
    void criarSchemaV39ComClienteHistorico() {
        schema = "it_cliente_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("39").migrate();

        condicaoId = jdbc.queryForObject("""
                INSERT INTO %s.condicao_pagamento
                    (nome, numero_parcelas, percentual_juros, percentual_multa,
                     percentual_desconto, ativo, created_at, updated_at)
                VALUES ('Condicao historica cliente', 1, 0, 0, 0, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class);
        clienteId = jdbc.queryForObject("""
                INSERT INTO %s.cliente
                    (nome, tipo, documento, condicao_pagamento_id,
                     limite_credito, ativo, created_at, updated_at)
                VALUES ('Cliente historico', 'FISICA', '12345678901', ?,
                        0, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class, condicaoId);
    }

    @AfterAll
    void removerSchemaTemporario() {
        if (schema != null && schema.matches("it_cliente_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV39ParaV40SemAssociarClienteHistorico() {
        flywayAte("40").migrate();

        var linha = jdbc.queryForMap("""
                SELECT id, organizacao_id, condicao_pagamento_id
                FROM %s.cliente WHERE id = ?
                """.formatted(schema), clienteId);
        assertThat(linha.get("id")).isEqualTo(clienteId);
        assertThat(linha.get("organizacao_id")).isNull();
        assertThat(linha.get("condicao_pagamento_id")).isEqualTo(condicaoId);

        Integer versao = jdbc.queryForObject("""
                SELECT max(version::integer) FROM %s.flyway_schema_history
                WHERE success = true
                """.formatted(schema), Integer.class);
        assertThat(versao).isEqualTo(40);
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
