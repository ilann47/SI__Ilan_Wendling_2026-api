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
class CatalogoConvenienciaHardeningMigrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void migraV50ParaV51InicializandoVersoesSemAlterarOwners() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "50").migrate();
            Long unidade = jdbc.queryForObject("""
                    INSERT INTO %s.unidade_medida
                        (nome,sigla,ativo,created_at,updated_at)
                    VALUES ('Historica','HIS',true,now(),now()) RETURNING id
                    """.formatted(schema), Long.class);
            flywayAte(schema, "51").migrate();
            var linha = jdbc.queryForMap(
                    "SELECT organizacao_id,version,nome FROM " + schema
                            + ".unidade_medida WHERE id=?", unidade);
            assertThat(linha.get("organizacao_id")).isNull();
            assertThat(linha.get("version")).isEqualTo(0L);
            assertThat(linha.get("nome")).isEqualTo("Historica");
        } finally {
            limpar(schema);
        }
    }

    @Test
    void nomeHistoricoDuplicadoDaUnidadeFalhaAtomicamenteEMantemV50() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "50").migrate();
            jdbc.update("""
                    INSERT INTO %s.unidade_medida
                        (nome,sigla,ativo,created_at,updated_at)
                    VALUES ('Caixa','CX1',true,now(),now()),
                           (' caixa ','CX2',true,now(),now())
                    """.formatted(schema));
            assertThatThrownBy(() -> flywayAte(schema, "51").migrate())
                    .hasStackTraceContaining("uq_unidade_medida_legado_nome");
            Integer versao = jdbc.queryForObject("SELECT max(version::integer) FROM " + schema
                    + ".flyway_schema_history WHERE success=true", Integer.class);
            Integer colunas = jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema=? AND table_name='unidade_medida' AND column_name='version'
                    """, Integer.class, schema);
            assertThat(versao).isEqualTo(50);
            assertThat(colunas).isZero();
        } finally {
            limpar(schema);
        }
    }

    @Test
    void permiteMesmoNomeDeUnidadeEmTenantsDiferentesEHistorico() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "50").migrate();
            Long organizacaoA = inserirOrganizacao(schema, "50111111000151", "Tenant A");
            Long organizacaoB = inserirOrganizacao(schema, "50222222000152", "Tenant B");
            jdbc.update("""
                    INSERT INTO %s.unidade_medida
                        (organizacao_id,nome,sigla,ativo,created_at,updated_at)
                    VALUES (?,'Caixa','CXA',true,now(),now()),
                           (?,' caixa ','CXB',true,now(),now()),
                           (NULL,'CAIXA','CXL',true,now(),now())
                    """.formatted(schema), organizacaoA, organizacaoB);

            flywayAte(schema, "51").migrate();

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".unidade_medida",
                    Integer.class)).isEqualTo(3);
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

    private String novoSchema() {
        String schema = "it_catalogo_v51_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        return schema;
    }

    private void limpar(String schema) {
        if (schema.matches("it_catalogo_v51_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Flyway flywayAte(String schema, String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
