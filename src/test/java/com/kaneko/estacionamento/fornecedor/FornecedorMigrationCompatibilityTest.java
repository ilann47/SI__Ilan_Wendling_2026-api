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

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FornecedorMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    private String schema;
    private Long fornecedorId;
    private Long condicaoId;
    private Long transportadoraId;

    @BeforeAll
    void prepararV41() {
        schema = "it_fornecedor_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("41").migrate();
        condicaoId = jdbc.queryForObject("""
                INSERT INTO %s.condicao_pagamento
                    (nome, numero_parcelas, percentual_juros, percentual_multa,
                     percentual_desconto, ativo, created_at, updated_at)
                VALUES ('Condicao historica', 1, 0, 0, 0, true, now(), now()) RETURNING id
                """.formatted(schema), Long.class);
        transportadoraId = jdbc.queryForObject("""
                INSERT INTO %s.transportadora
                    (nome, tipo, documento, ativo, created_at, updated_at)
                VALUES ('Transportadora historica', 'JURIDICA', '12345678000190', true, now(), now()) RETURNING id
                """.formatted(schema), Long.class);
        fornecedorId = jdbc.queryForObject("""
                INSERT INTO %s.fornecedor
                    (nome, tipo, documento, condicao_pagamento_id, transportadora_id,
                     limite_credito, ativo, created_at, updated_at)
                VALUES ('Fornecedor historico', 'JURIDICA', '99887766000155', ?, ?, 0, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class, condicaoId, transportadoraId);
        jdbc.update("""
                INSERT INTO %s.fornecedor_email
                    (fornecedor_id, email, principal, ativo, created_at, updated_at)
                VALUES (?, 'historico@teste.com', true, true, now(), now())
                """.formatted(schema), fornecedorId);
    }

    @AfterAll
    void limpar() {
        if (schema != null && schema.matches("it_fornecedor_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV41ParaV42PreservandoGrafoHistorico() {
        flywayAte("42").migrate();
        var linha = jdbc.queryForMap("""
                SELECT organizacao_id, condicao_pagamento_id, transportadora_id
                FROM %s.fornecedor WHERE id = ?
                """.formatted(schema), fornecedorId);
        assertThat(linha.get("organizacao_id")).isNull();
        assertThat(linha.get("condicao_pagamento_id")).isEqualTo(condicaoId);
        assertThat(linha.get("transportadora_id")).isEqualTo(transportadoraId);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM " + schema + ".fornecedor_email WHERE fornecedor_id = ?",
                Integer.class, fornecedorId)).isEqualTo(1);
    }

    private Flyway flywayAte(String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
