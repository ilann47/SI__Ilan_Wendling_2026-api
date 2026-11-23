package com.kaneko.estacionamento.fornecedor;

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
class FornecedorHardeningMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    private String schema;
    @AfterEach
    void limpar() {
        if (schema != null && schema.matches("it_supplier_hardening_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV43ParaV44PreservandoGrafoHistorico() {
        prepararV43();
        Long fornecedorId = inserirFornecedor("12345678000190");
        jdbc.update("""
                INSERT INTO %s.nota_entrada
                    (numero, fornecedor_id, created_at, updated_at)
                VALUES ('H-1', ?, now(), now())
                """.formatted(schema), fornecedorId);

        flywayAte("44").migrate();
        assertThat(jdbc.queryForObject(
                "SELECT organizacao_id FROM " + schema + ".fornecedor WHERE id = ?",
                Long.class, fornecedorId)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM " + schema + ".nota_entrada WHERE fornecedor_id = ?",
                Integer.class, fornecedorId)).isEqualTo(1);
    }

    @Test
    void v44RecusaColisaoDocumentalNormalizadaSemAplicacaoParcial() {
        prepararV43();
        inserirFornecedor("12.345.678/0001-90");
        inserirFornecedor("12345678000190");

        Throwable falha = catchThrowable(() -> flywayAte("44").migrate());

        assertThat(falha).isNotNull();
        assertThat(causaRaiz(falha).getMessage())
                .contains("documentos de fornecedor colidem apos normalizacao");
        assertThat(flywayAte("44").info().current().getVersion().getVersion()).isEqualTo("43");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname = ?
                  AND indexname IN ('uq_fornecedor_organizacao_documento',
                                    'uq_fornecedor_legado_documento')
                """, Integer.class, schema)).isEqualTo(2);
    }

    private void prepararV43() {
        schema = "it_supplier_hardening_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("43").migrate();
    }

    private Long inserirFornecedor(String documento) {
        return jdbc.queryForObject("""
                INSERT INTO %s.fornecedor
                    (nome, tipo, documento, limite_credito, ativo, created_at, updated_at)
                VALUES ('Historico', 'JURIDICA', ?, 0, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class, documento);
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
