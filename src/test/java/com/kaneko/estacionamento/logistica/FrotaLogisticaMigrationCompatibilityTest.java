package com.kaneko.estacionamento.logistica;

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
class FrotaLogisticaMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    private String schema;
    private Long transportadoraId;
    private Long veiculoId;
    private Long vinculoId;

    @BeforeAll
    void prepararHistorico() {
        schema = "it_frota_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("40").migrate();
        transportadoraId = jdbc.queryForObject("""
                INSERT INTO %s.transportadora (nome,tipo,documento,ativo,created_at,updated_at)
                VALUES ('Historica','JURIDICA','44123456000177',true,now(),now()) RETURNING id
                """.formatted(schema), Long.class);
        veiculoId = jdbc.queryForObject("""
                INSERT INTO %s.veiculo_frota (placa,ativo,created_at,updated_at)
                VALUES ('HIS1A23',true,now(),now()) RETURNING id
                """.formatted(schema), Long.class);
        vinculoId = jdbc.queryForObject("""
                INSERT INTO %s.transportadora_veiculo_frota
                    (transportadora_id,veiculo_frota_id,ativo,created_at,updated_at)
                VALUES (?,?,true,now(),now()) RETURNING id
                """.formatted(schema), Long.class, transportadoraId, veiculoId);
        flywayAte("47").migrate();
    }

    @AfterAll
    void limpar() {
        if (schema != null && schema.matches("it_frota_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migraV47ParaV48PreservandoGrafoHistorico() {
        flywayAte("48").migrate();
        Map<String,Object> veiculo = jdbc.queryForMap(
                "SELECT organizacao_id,placa FROM " + schema + ".veiculo_frota WHERE id=?", veiculoId);
        Map<String,Object> vinculo = jdbc.queryForMap("""
                SELECT organizacao_id,transportadora_id,veiculo_frota_id,ativo
                FROM %s.transportadora_veiculo_frota WHERE id=?
                """.formatted(schema), vinculoId);
        assertThat(veiculo.get("organizacao_id")).isNull();
        assertThat(veiculo.get("placa")).isEqualTo("HIS1A23");
        assertThat(vinculo.get("organizacao_id")).isNull();
        assertThat(vinculo.get("transportadora_id")).isEqualTo(transportadoraId);
        assertThat(vinculo.get("veiculo_frota_id")).isEqualTo(veiculoId);
        assertThat(vinculo.get("ativo")).isEqualTo(true);
    }

    private Flyway flywayAte(String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }
}
