package com.kaneko.estacionamento.logistica;

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
class FrotaLogisticaMigrationPreflightTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void colisaoNormalizadaFalhaAtomicamenteEMantemV47() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "47").migrate();
            jdbc.update("""
                    INSERT INTO %s.veiculo_frota (placa,ativo,created_at,updated_at)
                    VALUES ('ABC1D23',true,now(),now()),
                           (' abc1d23 ',true,now(),now())
                    """.formatted(schema));

            assertThatThrownBy(() -> flywayAte(schema, "48").migrate())
                    .hasStackTraceContaining("uq_veiculo_frota_legado_placa");
            assertV47SemColunaOwner(schema);
        } finally {
            limpar(schema);
        }
    }

    @Test
    void transportadoraPromovidaComFrotaFalhaAtomicamenteEMantemV47() throws Exception {
        String schema = novoSchema();
        try {
            flywayAte(schema, "40").migrate();
            Long transportadora = jdbc.queryForObject("""
                    INSERT INTO %s.transportadora
                        (nome,tipo,documento,ativo,created_at,updated_at)
                    VALUES ('Historica','JURIDICA','44123456000188',true,now(),now())
                    RETURNING id
                    """.formatted(schema), Long.class);
            Long veiculo = jdbc.queryForObject("""
                    INSERT INTO %s.veiculo_frota (placa,ativo,created_at,updated_at)
                    VALUES ('PRO1A23',true,now(),now()) RETURNING id
                    """.formatted(schema), Long.class);
            jdbc.update("""
                    INSERT INTO %s.transportadora_veiculo_frota
                        (transportadora_id,veiculo_frota_id,ativo,created_at,updated_at)
                    VALUES (?,?,true,now(),now())
                    """.formatted(schema), transportadora, veiculo);
            flywayAte(schema, "47").migrate();
            Long organizacao = inserirOrganizacao(schema);
            promoverTransportadora(schema, organizacao, transportadora);

            assertThatThrownBy(() -> flywayAte(schema, "48").migrate())
                    .hasStackTraceContaining("fk_tvf_transportadora_organizacao");
            assertV47SemColunaOwner(schema);
        } finally {
            limpar(schema);
        }
    }

    private void assertV47SemColunaOwner(String schema) {
        Integer versao = jdbc.queryForObject("""
                SELECT max(version::integer) FROM %s.flyway_schema_history WHERE success=true
                """.formatted(schema), Integer.class);
        Integer colunas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema=? AND table_name='veiculo_frota'
                  AND column_name='organizacao_id'
                """, Integer.class, schema);
        assertThat(versao).isEqualTo(47);
        assertThat(colunas).isZero();
    }

    private Long inserirOrganizacao(String schema) {
        return jdbc.queryForObject("""
                INSERT INTO %s.organizacao
                    (documento,razao_social,moeda,fuso_horario,regiao,plano,status)
                VALUES ('12345678000199','Organizacao','BRL','America/Sao_Paulo',
                        'BR-PR','ENTERPRISE','ATIVA') RETURNING id
                """.formatted(schema), Long.class);
    }

    private void promoverTransportadora(String schema, Long organizacao, Long transportadora)
            throws Exception {
        try (var connection = dataSource.getConnection();
             var searchPath = connection.createStatement()) {
            searchPath.execute("SET search_path TO " + schema + ", public");
            try (var update = connection.prepareStatement(
                    "UPDATE transportadora SET organizacao_id=? WHERE id=?")) {
                update.setLong(1, organizacao);
                update.setLong(2, transportadora);
                update.executeUpdate();
            } finally {
                searchPath.execute("RESET search_path");
            }
        }
    }

    private String novoSchema() {
        String schema = "it_frota_pre_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        return schema;
    }

    private void limpar(String schema) {
        if (schema.matches("it_frota_pre_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Flyway flywayAte(String schema, String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
