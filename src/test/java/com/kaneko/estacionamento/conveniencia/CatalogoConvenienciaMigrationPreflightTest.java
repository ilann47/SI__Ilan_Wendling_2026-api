package com.kaneko.estacionamento.conveniencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
class CatalogoConvenienciaMigrationPreflightTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"categoria", "marca", "unidade_medida"})
    void colisaoHistoricaNormalizadaFalhaAtomicamente(String tabela) {
        String schema = novoSchema();
        try {
            flywayAte(schema, "49").migrate();
            inserirColisao(schema, tabela);

            assertThatThrownBy(() -> flywayAte(schema, "50").migrate())
                    .hasStackTraceContaining(constraint(tabela));
            assertV49SemOwner(schema, tabela);
        } finally {
            limpar(schema);
        }
    }

    @Test
    void upgradeNaoFalhaComHistoricoNormalizadoValido() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "49").migrate();
            jdbc.update("INSERT INTO " + schema
                    + ".marca (nome,ativo,created_at,updated_at) VALUES ('Unica',true,now(),now())");
            flywayAte(schema, "50").migrate();
            Integer versao = jdbc.queryForObject("SELECT max(version::integer) FROM " + schema
                    + ".flyway_schema_history WHERE success=true", Integer.class);
            assertThat(versao).isEqualTo(50);
        } finally {
            limpar(schema);
        }
    }

    private void inserirColisao(String schema, String tabela) {
        if ("unidade_medida".equals(tabela)) {
            jdbc.update("INSERT INTO " + schema + ".unidade_medida "
                    + "(nome,sigla,ativo,created_at,updated_at) VALUES "
                    + "('Unidade A','UN',true,now(),now()),('Unidade B',' un ',true,now(),now())");
        } else {
            jdbc.update("INSERT INTO " + schema + "." + tabela
                    + " (nome,ativo,created_at,updated_at) VALUES "
                    + "('Duplicada',true,now(),now()),(' duplicada ',true,now(),now())");
        }
    }

    private String constraint(String tabela) {
        return switch (tabela) {
            case "categoria" -> "uq_categoria_legado_nome";
            case "marca" -> "uq_marca_legado_nome";
            default -> "uq_unidade_medida_legado_sigla";
        };
    }

    private void assertV49SemOwner(String schema, String tabela) {
        Integer versao = jdbc.queryForObject("SELECT max(version::integer) FROM " + schema
                + ".flyway_schema_history WHERE success=true", Integer.class);
        Integer colunas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema=? AND table_name=? AND column_name='organizacao_id'
                """, Integer.class, schema, tabela);
        assertThat(versao).isEqualTo(49);
        assertThat(colunas).isZero();
    }

    private String novoSchema() {
        String schema = "it_catalogo_pre_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        return schema;
    }

    private void limpar(String schema) {
        if (schema.matches("it_catalogo_pre_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Flyway flywayAte(String schema, String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
