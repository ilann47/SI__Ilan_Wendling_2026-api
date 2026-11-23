package com.kaneko.estacionamento.financeiro;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
class EscalaDecimalAdministrativaMigrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migraValoresAvulsosEPercentuaisSemRefazerTotaisOuSobrescreverMicros() {
        String schema = "it_micros_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrar(schema, "98");
            long condicao = jdbc.queryForObject("""
                    insert into %s.condicao_pagamento
                        (nome,percentual_juros,percentual_juros_micros,percentual_multa)
                    values ('Pagamento anterior',2.50,2500001,3.75) returning id
                    """.formatted(schema), Long.class);
            // A função legada da V39 resolve os catálogos no search_path da sessão.
            new TransactionTemplate(new JdbcTransactionManager(dataSource)).executeWithoutResult(tx -> {
                jdbc.execute("SET LOCAL search_path TO " + schema);
                jdbc.update("""
                        insert into parcela_condicao_pagamento
                            (condicao_pagamento_id,numero,dias,percentual)
                        values (?,1,30,100)
                        """, condicao);
            });
            long despesa = jdbc.queryForObject("""
                    insert into %s.contas_pagar_avulsa
                        (descricao,numero_nota,serie,modelo,valor_original,valor_pago,valor_total,data_vencimento,situacao)
                    values ('Valor registrado','000120','00A','055',12.34,1.23,12.35,current_date,'PARCIAL') returning id
                    """.formatted(schema), Long.class);
            OffsetDateTime anterior = jdbc.queryForObject("select updated_at from " + schema
                    + ".contas_pagar_avulsa where id=?", OffsetDateTime.class, despesa);

            migrar(schema, "99");

            assertThat(jdbc.queryForMap("""
                    select numero_nota,serie,modelo,valor_total_micros,valor_pago_micros,situacao
                    from %s.contas_pagar_avulsa where id=?
                    """.formatted(schema), despesa))
                    .containsEntry("numero_nota", "000120").containsEntry("serie", "00A")
                    .containsEntry("modelo", "055").containsEntry("situacao", "PARCIAL")
                    .containsEntry("valor_total_micros", new BigDecimal("12350000"))
                    .containsEntry("valor_pago_micros", new BigDecimal("1230000"));
            assertThat(jdbc.queryForObject("select updated_at from " + schema
                    + ".contas_pagar_avulsa where id=?", OffsetDateTime.class, despesa)).isEqualTo(anterior);
            assertThat(jdbc.queryForMap("""
                    select percentual_juros_micros,percentual_multa_micros
                    from %s.condicao_pagamento where id=?
                    """.formatted(schema), condicao))
                    .containsEntry("percentual_juros_micros", new BigDecimal("2500001"))
                    .containsEntry("percentual_multa_micros", new BigDecimal("3750000"));
            assertThat(jdbc.queryForObject("select percentual_micros from " + schema
                    + ".parcela_condicao_pagamento where condicao_pagamento_id=?", Long.class, condicao))
                    .isEqualTo(100000000L);
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema=? and data_type='numeric' and numeric_scale <> 0 and table_name in
                    ('cargo','cliente','condicao_pagamento','contas_pagar_avulsa','fornecedor','funcionario',
                     'mensalista','movimentacao','parcela_condicao_pagamento','tarifa','veiculo_frota')
                    """, Integer.class, schema)).isZero();
        } finally {
            if (schema.matches("it_micros_[a-f0-9]{32}")) jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private void migrar(String schema, String versao) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration", "classpath:db/test-cutover")
                .target(MigrationVersion.fromVersion(versao)).load().migrate();
    }
}
