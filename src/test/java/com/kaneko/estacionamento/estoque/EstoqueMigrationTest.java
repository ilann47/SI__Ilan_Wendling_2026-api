package com.kaneko.estacionamento.estoque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
class EstoqueMigrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void migraV53ParaV54SemBackfillDoHistorico() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "53").migrate();
            Long produtoHistorico = inserirProduto(schema, null, "H", "2.500");

            flywayAte(schema, "54").migrate();

            assertThat(jdbc.queryForObject(
                    "SELECT quantidade FROM " + schema + ".produto WHERE id=?",
                    java.math.BigDecimal.class, produtoHistorico)).isEqualByComparingTo("2.500");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".local_estoque", Integer.class)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".saldo_estoque", Integer.class)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".movimento_estoque", Integer.class)).isZero();
        } finally {
            limpar(schema);
        }
    }

    @Test
    void preflightRecusaProdutoTenantComQuantidadeLegadaEReverteAtomicamente() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "53").migrate();
            Long org = inserirOrganizacao(schema);
            inserirProduto(schema, org, "T", "2.500");

            assertThatThrownBy(() -> flywayAte(schema, "54").migrate())
                    .hasStackTraceContaining("ck_estoque_produto_tenant_sem_saldo_legado");
            Integer versao = jdbc.queryForObject("SELECT max(version::integer) FROM " + schema
                    + ".flyway_schema_history WHERE success=true", Integer.class);
            Integer tabelas = jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema=? AND table_name IN
                        ('local_estoque','saldo_estoque','movimento_estoque')
                    """, Integer.class, schema);
            assertThat(versao).isEqualTo(53);
            assertThat(tabelas).isZero();
        } finally {
            limpar(schema);
        }
    }

    private Long inserirOrganizacao(String schema) {
        String documento = "57" + Math.abs(schema.hashCode()) + "0001";
        documento = (documento + "00000000000000").substring(0, 14);
        return jdbc.queryForObject("""
                INSERT INTO %s.organizacao
                    (documento,razao_social,moeda,fuso_horario,regiao,plano,status,
                     version,created_at,updated_at)
                VALUES (?,'Tenant Estoque','BRL','America/Sao_Paulo','BR-PR','ENTERPRISE',
                        'ATIVA',0,now(),now()) RETURNING id
                """.formatted(schema), Long.class, documento);
    }

    private Long inserirProduto(String schema, Long org, String sufixo, String quantidade) {
        Long marca = jdbc.queryForObject("""
                INSERT INTO %s.marca (organizacao_id,nome,ativo,version,created_at,updated_at)
                VALUES (?,'Marca Estoque %s',true,0,now(),now()) RETURNING id
                """.formatted(schema, sufixo), Long.class, org);
        Long unidade = jdbc.queryForObject("""
                INSERT INTO %s.unidade_medida
                    (organizacao_id,nome,sigla,ativo,version,created_at,updated_at)
                VALUES (?,'Unidade Estoque %s','E%s',true,0,now(),now()) RETURNING id
                """.formatted(schema, sufixo, sufixo), Long.class, org);
        return jdbc.queryForObject("""
                INSERT INTO %s.produto
                    (organizacao_id,nome,marca_id,unidade_medida_id,quantidade,
                     ativo,version,created_at,updated_at)
                VALUES (?,'Produto Estoque %s',?,?,?,true,0,now(),now()) RETURNING id
                """.formatted(schema, sufixo), Long.class, org, marca, unidade,
                new java.math.BigDecimal(quantidade));
    }

    private String novoSchema() {
        String schema = "it_estoque_v54_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        return schema;
    }

    private void limpar(String schema) {
        if (schema.matches("it_estoque_v54_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Flyway flywayAte(String schema, String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
