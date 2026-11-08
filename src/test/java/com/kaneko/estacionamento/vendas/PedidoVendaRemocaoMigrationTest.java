package com.kaneko.estacionamento.vendas;

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
class PedidoVendaRemocaoMigrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void removeEsquemaDoPedidoSemApagarNotaAvulsaEValidaReversao() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "81").migrate();
            Contexto contexto = criarContexto(schema);
            inserirNota(schema, contexto, "NS-AVULSA", null);

            flywayAte(schema, "82").migrate();

            assertThat(versao(schema)).isEqualTo(82);
            assertThat(tabelasAntigas(schema)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                     WHERE table_schema=? AND column_name='venda_administrativa_id'
                    """, Integer.class, schema)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema
                    + ".permissao WHERE codigo IN ('sales:read','sales:manage')", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema
                    + ".papel WHERE codigo='COMERCIAL'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema
                    + ".nota_saida WHERE numero='NS-AVULSA'", Integer.class)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("UPDATE " + schema
                    + ".nota_saida SET revertida_em=now(), motivo_reversao='teste' WHERE numero='NS-AVULSA'"))
                    .hasStackTraceContaining("ck_nota_saida_reversao");
            assertThat(jdbc.update("UPDATE " + schema + ".nota_saida"
                    + " SET situacao='CANCELADA', revertida_em=now(), motivo_reversao='teste'"
                    + " WHERE numero='NS-AVULSA'")).isEqualTo(1);
        } finally {
            limparSchema(schema);
        }
    }

    @Test
    void recusaApagarOrigemDeNotaVinculadaEReverteAtomicamente() {
        String schema = novoSchema();
        try {
            flywayAte(schema, "81").migrate();
            Contexto contexto = criarContexto(schema);
            Long venda = jdbc.queryForObject("""
                    INSERT INTO %s.venda_administrativa
                      (organizacao_id, cliente_id, local_estoque_id, numero, status,
                       data_emissao, moeda, subtotal, valor_total)
                    VALUES (?, ?, ?, 'VENDA-LEGADA', 'CONFIRMADA', CURRENT_DATE, 'BRL', 0, 0)
                    RETURNING id
                    """.formatted(schema), Long.class, contexto.org(), contexto.cliente(), contexto.local());
            inserirNota(schema, contexto, "NS-LEGADA", venda);

            assertThatThrownBy(() -> flywayAte(schema, "82").migrate())
                    .hasStackTraceContaining("V82: Pedido de Venda ainda possui efeitos");
            assertThat(versao(schema)).isEqualTo(81);
            assertThat(tabelasAntigas(schema)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT venda_administrativa_id FROM " + schema
                    + ".nota_saida WHERE numero='NS-LEGADA'", Long.class)).isEqualTo(venda);
        } finally {
            limparSchema(schema);
        }
    }

    private Contexto criarContexto(String schema) {
        Long org = jdbc.queryForObject("""
                INSERT INTO %s.organizacao (documento, razao_social, moeda, fuso_horario,
                    regiao, plano, status, created_at, updated_at)
                VALUES ('11222333000181','Organização V82','BRL','America/Sao_Paulo',
                    'BR-PR','ENTERPRISE','ATIVA',now(),now()) RETURNING id
                """.formatted(schema), Long.class);
        Long cliente = jdbc.queryForObject("""
                INSERT INTO %s.cliente (organizacao_id,nome,documento)
                VALUES (?,'Cliente V82','52998224725') RETURNING id
                """.formatted(schema), Long.class, org);
        Long local = jdbc.queryForObject("""
                INSERT INTO %s.local_estoque (organizacao_id,nome)
                VALUES (?,'Estoque V82') RETURNING id
                """.formatted(schema), Long.class, org);
        return new Contexto(org, cliente, local);
    }

    private void inserirNota(String schema, Contexto contexto, String numero, Long venda) {
        jdbc.update("""
                INSERT INTO %s.nota_saida
                    (organizacao_id, cliente_id, local_estoque_id, numero, venda_administrativa_id)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(schema), contexto.org(), contexto.cliente(), contexto.local(), numero, venda);
    }

    private int versao(String schema) {
        return jdbc.queryForObject("SELECT max(version::integer) FROM " + schema
                + ".flyway_schema_history WHERE success", Integer.class);
    }

    private int tabelasAntigas(String schema) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                 WHERE table_schema=? AND table_name IN ('venda_administrativa','item_venda_administrativa')
                """, Integer.class, schema);
    }

    private String novoSchema() {
        String schema = "it_venda_v82_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        return schema;
    }

    private void limparSchema(String schema) {
        if (schema.matches("it_venda_v82_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Flyway flywayAte(String schema, String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }

    private record Contexto(Long org, Long cliente, Long local) {}
}
