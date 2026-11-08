package com.kaneko.estacionamento.conveniencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
class PrecisaoComercialMigrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void preservaPrecoLegadoPrecoCanonicoMargemECustoRegistrados() {
        String schema = "it_comercial_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrar(schema, "99");
            long marca = jdbc.queryForObject("insert into " + schema
                    + ".marca (nome) values ('Marca') returning id", Long.class);
            long unidade = jdbc.queryForObject("insert into " + schema
                    + ".unidade_medida (nome,sigla) values ('Unidade','UN') returning id", Long.class);
            long fornecedor = jdbc.queryForObject("insert into " + schema
                    + ".fornecedor (nome,documento) values ('Fornecedor','12345678901234') returning id", Long.class);
            long legado = jdbc.queryForObject("""
                    insert into %s.produto (nome,marca_id,unidade_medida_id,valor_venda,percentual_lucro)
                    values ('Legado',?,?,12.34,-13.25) returning id
                    """.formatted(schema), Long.class, marca, unidade);
            long preciso = jdbc.queryForObject("""
                    insert into %s.produto (nome,marca_id,unidade_medida_id,valor_venda,valor_venda_micros)
                    values ('Preciso',?,?,0,120000001) returning id
                    """.formatted(schema), Long.class, marca, unidade);
            long vinculo = jdbc.queryForObject("""
                    insert into %s.produto_fornecedor (produto_id,fornecedor_id,custo)
                    values (?,?,5.67) returning id
                    """.formatted(schema), Long.class, legado, fornecedor);
            var metadados = jdbc.queryForMap("select id,version,updated_at from " + schema
                    + ".produto where id=?", legado);

            migrar(schema, "100");

            assertThat(jdbc.queryForMap("select id,version,updated_at from " + schema
                    + ".produto where id=?", legado)).isEqualTo(metadados);
            assertThat(valor(schema, "produto", "valor_venda_micros", legado)).isEqualByComparingTo("12340000");
            assertThat(valor(schema, "produto", "valor_venda_micros", preciso)).isEqualByComparingTo("120000001");
            assertThat(valor(schema, "produto", "percentual_lucro_micros", legado)).isEqualByComparingTo("-13250000");
            assertThat(valor(schema, "produto_fornecedor", "custo_micros", vinculo)).isEqualByComparingTo("5670000");
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema=? and data_type='numeric' and numeric_scale is distinct from 0
                    """, Integer.class, schema)).isZero();
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema=? and table_name in
                      ('produto','produto_fornecedor','lote_preco','reserva_inventario','pedido','item_pedido')
                      and numeric_scale=0 and right(column_name,7)='_micros' and is_generated='NEVER'
                    """, Integer.class, schema)).isEqualTo(15);
        } finally {
            if (schema.matches("it_comercial_[a-f0-9]{32}")) jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private BigDecimal valor(String schema, String tabela, String coluna, long id) {
        return jdbc.queryForObject("select " + coluna + " from " + schema + "." + tabela + " where id=?",
                BigDecimal.class, id);
    }

    private void migrar(String schema, String versao) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration", "classpath:db/test-cutover")
                .target(MigrationVersion.fromVersion(versao)).load().migrate();
    }
}
