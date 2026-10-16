package com.kaneko.estacionamento.conveniencia;

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

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogoConvenienciaMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    private String schema;
    private Long categoriaId;
    private Long marcaId;
    private Long unidadeId;
    private Long produtoId;

    @BeforeAll
    void prepararV49() {
        schema = "it_catalogo_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("49").migrate();
        categoriaId = jdbc.queryForObject("""
                INSERT INTO %s.categoria (nome,descricao,ativo,created_at,updated_at)
                VALUES ('Historica','Descricao',true,now(),now()) RETURNING id
                """.formatted(schema), Long.class);
        marcaId = jdbc.queryForObject("""
                INSERT INTO %s.marca (nome,ativo,created_at,updated_at)
                VALUES ('Marca historica',true,now(),now()) RETURNING id
                """.formatted(schema), Long.class);
        unidadeId = jdbc.queryForObject("""
                INSERT INTO %s.unidade_medida (nome,sigla,ativo,created_at,updated_at)
                VALUES ('Unidade historica','HIS',true,now(),now()) RETURNING id
                """.formatted(schema), Long.class);
        produtoId = jdbc.queryForObject("""
                INSERT INTO %s.produto
                    (nome,marca_id,unidade_medida_id,categoria_id,ativo,created_at,updated_at)
                VALUES ('Produto historico',?,?,?,true,now(),now()) RETURNING id
                """.formatted(schema), Long.class, marcaId, unidadeId, categoriaId);
    }

    @AfterAll
    void limpar() {
        if (schema != null && schema.matches("it_catalogo_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV49ParaV50PreservandoCatalogoEProdutoHistoricos() {
        flywayAte("50").migrate();
        Map<String, Object> produto = jdbc.queryForMap("""
                SELECT p.id,p.marca_id,p.unidade_medida_id,p.categoria_id,
                       m.organizacao_id AS marca_org,
                       u.organizacao_id AS unidade_org,
                       c.organizacao_id AS categoria_org
                FROM %s.produto p
                JOIN %s.marca m ON m.id=p.marca_id
                JOIN %s.unidade_medida u ON u.id=p.unidade_medida_id
                JOIN %s.categoria c ON c.id=p.categoria_id
                WHERE p.id=?
                """.formatted(schema, schema, schema, schema), produtoId);

        assertThat(produto.get("marca_id")).isEqualTo(marcaId);
        assertThat(produto.get("unidade_medida_id")).isEqualTo(unidadeId);
        assertThat(produto.get("categoria_id")).isEqualTo(categoriaId);
        assertThat(produto.get("marca_org")).isNull();
        assertThat(produto.get("unidade_org")).isNull();
        assertThat(produto.get("categoria_org")).isNull();
        Integer versao = jdbc.queryForObject("""
                SELECT max(version::integer) FROM %s.flyway_schema_history WHERE success=true
                """.formatted(schema), Integer.class);
        assertThat(versao).isEqualTo(50);
    }

    private Flyway flywayAte(String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
