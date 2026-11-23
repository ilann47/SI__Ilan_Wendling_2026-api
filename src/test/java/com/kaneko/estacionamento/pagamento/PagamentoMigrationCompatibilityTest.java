package com.kaneko.estacionamento.pagamento;

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
class PagamentoMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    private String schema;

    @BeforeAll
    void criarSchemaV37ComHistorico() {
        schema = "it_pagamento_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("37").migrate();

        Long formaId = jdbc.queryForObject("""
                INSERT INTO %s.forma_pagamento
                    (nome, descricao, ativo, created_at, updated_at)
                VALUES ('Forma historica', 'Legado', true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class);
        Long condicaoId = jdbc.queryForObject("""
                INSERT INTO %s.condicao_pagamento
                    (nome, numero_parcelas, percentual_juros, percentual_multa,
                     percentual_desconto, ativo, created_at, updated_at)
                VALUES ('Condicao historica', 1, 0, 0, 0, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class);
        jdbc.update("""
                INSERT INTO %s.parcela_condicao_pagamento
                    (condicao_pagamento_id, numero, dias, percentual,
                     forma_pagamento_id, created_at, updated_at)
                VALUES (?, 1, 0, 100, ?, now(), now())
                """.formatted(schema), condicaoId, formaId);
    }

    @AfterAll
    void removerSchemaTemporario() {
        if (schema != null && schema.matches("it_pagamento_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV37ParaV39SemAlterarGrafoHistorico() {
        flywayAte("39").migrate();

        var linha = jdbc.queryForMap("""
                SELECT f.id AS forma_id, f.organizacao_id AS forma_org,
                       c.id AS condicao_id, c.organizacao_id AS condicao_org,
                       p.organizacao_id AS parcela_org,
                       p.condicao_pagamento_id, p.forma_pagamento_id
                FROM %s.parcela_condicao_pagamento p
                JOIN %s.condicao_pagamento c ON c.id = p.condicao_pagamento_id
                JOIN %s.forma_pagamento f ON f.id = p.forma_pagamento_id
                """.formatted(schema, schema, schema));

        assertThat(linha.get("forma_org")).isNull();
        assertThat(linha.get("condicao_org")).isNull();
        assertThat(linha.get("parcela_org")).isNull();
        assertThat(linha.get("condicao_pagamento_id")).isEqualTo(linha.get("condicao_id"));
        assertThat(linha.get("forma_pagamento_id")).isEqualTo(linha.get("forma_id"));

        Integer versao = jdbc.queryForObject("""
                SELECT max(version::integer) FROM %s.flyway_schema_history
                WHERE success = true
                """.formatted(schema), Integer.class);
        assertThat(versao).isEqualTo(39);
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
