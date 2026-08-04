package com.kaneko.estacionamento.conveniencia;

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
class ProdutoFornecedorMigrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void migraV52ParaV53PreservandoVinculoHistorico() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "52").migrate();
            fixarSearchPathLegado(schema);
            Long produto = inserirProduto(schema, null, "H");
            Long fornecedor = inserirFornecedor(schema, null, "53990000000101");
            Long vinculo = jdbc.queryForObject("""
                    INSERT INTO %s.produto_fornecedor
                        (produto_id,fornecedor_id,codigo_prod,custo,ativo,created_at,updated_at)
                    VALUES (?,?,'LEG',4.50,false,now(),now()) RETURNING id
                    """.formatted(schema), Long.class, produto, fornecedor);

            flywayAte(schema, "53").migrate();

            var linha = jdbc.queryForMap("SELECT organizacao_id,version,produto_id,"
                    + "fornecedor_id,codigo_prod,custo,ativo FROM " + schema
                    + ".produto_fornecedor WHERE id=?", vinculo);
            assertThat(linha.get("organizacao_id")).isNull();
            assertThat(linha.get("version")).isEqualTo(0L);
            assertThat(linha.get("produto_id")).isEqualTo(produto);
            assertThat(linha.get("fornecedor_id")).isEqualTo(fornecedor);
            assertThat(linha.get("ativo")).isEqualTo(false);
        } finally {
            limpar(schema);
        }
    }

    @Test
    void preflightRecusaGrafoPreexistenteComOwnerEReverteAtomicamente() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "52").migrate();
            Long org = inserirOrganizacao(schema, "53990000000102");
            Long produto = inserirProduto(schema, org, "T");
            Long fornecedor = inserirFornecedor(schema, org, "53990000000103");
            jdbc.execute("ALTER TABLE " + schema
                    + ".produto_fornecedor DISABLE TRIGGER USER");
            jdbc.update("INSERT INTO " + schema
                    + ".produto_fornecedor (produto_id,fornecedor_id) VALUES (?,?)",
                    produto, fornecedor);
            jdbc.execute("ALTER TABLE " + schema
                    + ".produto_fornecedor ENABLE TRIGGER USER");

            assertThatThrownBy(() -> flywayAte(schema, "53").migrate())
                    .hasStackTraceContaining("fk_produto_fornecedor_owner_preflight");
            Integer versao = jdbc.queryForObject("SELECT max(version::integer) FROM " + schema
                    + ".flyway_schema_history WHERE success=true", Integer.class);
            Integer colunas = jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema=? AND table_name='produto_fornecedor'
                      AND column_name='version'
                    """, Integer.class, schema);
            assertThat(versao).isEqualTo(52);
            assertThat(colunas).isZero();
        } finally {
            limpar(schema);
        }
    }

    private Long inserirOrganizacao(String schema, String documento) {
        return jdbc.queryForObject("""
                INSERT INTO %s.organizacao
                    (documento,razao_social,moeda,fuso_horario,regiao,plano,status,
                     version,created_at,updated_at)
                VALUES (?,'Tenant PF','BRL','America/Sao_Paulo','BR-PR','ENTERPRISE',
                        'ATIVA',0,now(),now()) RETURNING id
                """.formatted(schema), Long.class, documento);
    }

    private Long inserirProduto(String schema, Long org, String sufixo) {
        Long marca = jdbc.queryForObject("""
                INSERT INTO %s.marca (organizacao_id,nome,ativo,version,created_at,updated_at)
                VALUES (?,'Marca PF %s',true,0,now(),now()) RETURNING id
                """.formatted(schema, sufixo), Long.class, org);
        Long unidade = jdbc.queryForObject("""
                INSERT INTO %s.unidade_medida
                    (organizacao_id,nome,sigla,ativo,version,created_at,updated_at)
                VALUES (?,'Unidade PF %s','%s',true,0,now(),now()) RETURNING id
                """.formatted(schema, sufixo, "U" + sufixo), Long.class, org);
        return jdbc.queryForObject("""
                INSERT INTO %s.produto
                    (organizacao_id,nome,marca_id,unidade_medida_id,ativo,version,
                     created_at,updated_at)
                VALUES (?,'Produto PF %s',?,?,true,0,now(),now()) RETURNING id
                """.formatted(schema, sufixo), Long.class, org, marca, unidade);
    }

    private Long inserirFornecedor(String schema, Long org, String documento) {
        return jdbc.queryForObject("""
                INSERT INTO %s.fornecedor
                    (organizacao_id,nome,tipo,documento,limite_credito,ativo,
                     created_at,updated_at)
                VALUES (?,'Fornecedor PF','JURIDICA',?,0,true,now(),now()) RETURNING id
                """.formatted(schema), Long.class, org, documento);
    }

    private String novoSchema() {
        String schema = "it_produto_fornecedor_v53_"
                + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        return schema;
    }

    private void fixarSearchPathLegado(String schema) {
        jdbc.execute("ALTER FUNCTION " + schema
                + ".validar_fornecedor_historico_em_consumer() SET search_path = " + schema);
    }

    private void limpar(String schema) {
        if (schema.matches("it_produto_fornecedor_v53_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Flyway flywayAte(String schema, String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
