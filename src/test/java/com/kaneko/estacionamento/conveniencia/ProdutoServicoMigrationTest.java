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
class ProdutoServicoMigrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void migraV51ParaV52PreservandoProdutoEServicoHistoricos() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "51").migrate();
            fixarSearchPathV50(schema);
            Long marca = inserirMarca(schema, null, "Historica");
            Long unidade = inserirUnidade(schema, null, "Historica", "HIS");
            Long produto = inserirProdutoHistoricoV51(schema, marca, unidade, "LEG-1");
            Long servico = inserirServicoHistoricoV51(schema, "Servico historico");

            flywayAte(schema, "52").migrate();

            assertThat(jdbc.queryForMap("SELECT organizacao_id,version FROM " + schema
                    + ".produto WHERE id=?", produto))
                    .containsEntry("version", 0L).containsEntry("organizacao_id", null);
            assertThat(jdbc.queryForMap("SELECT organizacao_id,version FROM " + schema
                    + ".servico WHERE id=?", servico))
                    .containsEntry("version", 0L).containsEntry("organizacao_id", null);
        } finally {
            limpar(schema);
        }
    }

    @Test
    void preflightDeCodigoDeBarrasFalhaAtomicamente() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "51").migrate();
            fixarSearchPathV50(schema);
            Long marca = inserirMarca(schema, null, "Historica");
            Long unidade = inserirUnidade(schema, null, "Historica", "HIS");
            inserirProdutoHistoricoV51(schema, marca, unidade, "ABC");
            inserirProdutoHistoricoV51(schema, marca, unidade, " ABC ");
            assertThatThrownBy(() -> flywayAte(schema, "52").migrate())
                    .hasStackTraceContaining("uq_produto_legado_codigo_barras");
            assertFalhaAtomicaV52(schema);
        } finally {
            limpar(schema);
        }
    }

    @Test
    void preflightDeNomeDeServicoFalhaAtomicamente() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "51").migrate();
            inserirServicoHistoricoV51(schema, "Lavagem");
            inserirServicoHistoricoV51(schema, " lavagem ");

            assertThatThrownBy(() -> flywayAte(schema, "52").migrate())
                    .hasStackTraceContaining("uq_servico_legado_nome");
            assertFalhaAtomicaV52(schema);
        } finally {
            limpar(schema);
        }
    }

    @Test
    void produtoExigeCatalogoDoMesmoTenantEUnicidadesSaoIsoladas() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "52").migrate();
            Long orgA = inserirOrganizacao(schema, "52111111000151", "Tenant A");
            Long orgB = inserirOrganizacao(schema, "52222222000152", "Tenant B");
            Long marcaA = inserirMarca(schema, orgA, "Marca A");
            Long marcaB = inserirMarca(schema, orgB, "Marca B");
            Long unidadeA = inserirUnidade(schema, orgA, "Unidade A", "UNA");
            Long unidadeB = inserirUnidade(schema, orgB, "Unidade B", "UNB");

            inserirProduto(schema, orgA, marcaA, unidadeA, "COD-1");
            inserirProduto(schema, orgB, marcaB, unidadeB, " COD-1 ");
            inserirServico(schema, orgA, "Lavagem");
            inserirServico(schema, orgB, " lavagem ");

            assertThatThrownBy(() -> inserirProduto(
                    schema, orgA, marcaB, unidadeA, "COD-2"))
                    .hasStackTraceContaining("fk_produto_marca_organizacao");
            assertThatThrownBy(() -> inserirProduto(
                    schema, orgA, marcaA, unidadeA, " COD-1 "))
                    .hasStackTraceContaining("uq_produto_organizacao_codigo_barras");
            assertThatThrownBy(() -> inserirServico(schema, orgA, " LAVAGEM "))
                    .hasStackTraceContaining("uq_servico_organizacao_nome");
        } finally {
            limpar(schema);
        }
    }

    private Long inserirOrganizacao(String schema, String documento, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO %s.organizacao
                    (documento,razao_social,moeda,fuso_horario,regiao,plano,status,
                     version,created_at,updated_at)
                VALUES (?,?,'BRL','America/Sao_Paulo','BR-PR','ENTERPRISE','ATIVA',
                        0,now(),now()) RETURNING id
                """.formatted(schema), Long.class, documento, nome);
    }

    private Long inserirMarca(String schema, Long org, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO %s.marca (organizacao_id,nome,ativo,version,created_at,updated_at)
                VALUES (?,?,true,0,now(),now()) RETURNING id
                """.formatted(schema), Long.class, org, nome);
    }

    private Long inserirUnidade(String schema, Long org, String nome, String sigla) {
        return jdbc.queryForObject("""
                INSERT INTO %s.unidade_medida
                    (organizacao_id,nome,sigla,ativo,version,created_at,updated_at)
                VALUES (?,?,?,true,0,now(),now()) RETURNING id
                """.formatted(schema), Long.class, org, nome, sigla);
    }

    private Long inserirProduto(
            String schema, Long org, Long marca, Long unidade, String codigo) {
        return jdbc.queryForObject("""
                INSERT INTO %s.produto
                    (organizacao_id,nome,codigo_barras,marca_id,unidade_medida_id,
                     ativo,version,created_at,updated_at)
                VALUES (?,'Produto',?,?,?,true,0,now(),now()) RETURNING id
                """.formatted(schema), Long.class, org, codigo, marca, unidade);
    }

    private Long inserirProdutoHistoricoV51(
            String schema, Long marca, Long unidade, String codigo) {
        return jdbc.queryForObject("""
                INSERT INTO %s.produto
                    (nome,codigo_barras,marca_id,unidade_medida_id,ativo,created_at,updated_at)
                VALUES ('Produto',?,?,?,true,now(),now()) RETURNING id
                """.formatted(schema), Long.class, codigo, marca, unidade);
    }

    private Long inserirServico(String schema, Long org, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO %s.servico
                    (organizacao_id,nome,ativo,version,created_at,updated_at)
                VALUES (?,?,true,0,now(),now()) RETURNING id
                """.formatted(schema), Long.class, org, nome);
    }

    private Long inserirServicoHistoricoV51(String schema, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO %s.servico (nome,ativo,created_at,updated_at)
                VALUES (?,true,now(),now()) RETURNING id
                """.formatted(schema), Long.class, nome);
    }

    private String novoSchema() {
        String schema = "it_produto_servico_v52_"
                + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        return schema;
    }

    private void fixarSearchPathV50(String schema) {
        jdbc.execute("ALTER FUNCTION " + schema
                + ".validar_produto_catalogo_historico() SET search_path = " + schema);
    }

    private void assertFalhaAtomicaV52(String schema) {
        Integer versao = jdbc.queryForObject("SELECT max(version::integer) FROM " + schema
                + ".flyway_schema_history WHERE success=true", Integer.class);
        Integer colunas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema=? AND table_name='produto' AND column_name='version'
                """, Integer.class, schema);
        assertThat(versao).isEqualTo(51);
        assertThat(colunas).isZero();
    }

    private void limpar(String schema) {
        if (schema.matches("it_produto_servico_v52_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Flyway flywayAte(String schema, String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
