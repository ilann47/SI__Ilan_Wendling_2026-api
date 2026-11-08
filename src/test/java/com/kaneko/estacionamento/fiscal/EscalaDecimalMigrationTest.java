package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
class EscalaDecimalMigrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void idaEVoltaExataComSeisCasasSemArredondamentoImplicito() {
        for (String valor : new String[] {"120", "0.000001", "-0.000001", "1.125001", "5.250001", "9999999999999.999999"}) {
            BigDecimal decimal = new BigDecimal(valor);
            BigDecimal inteiro = decimal.movePointRight(6).setScale(0, RoundingMode.UNNECESSARY);
            assertThat(inteiro.scale()).isZero();
            assertThat(inteiro.movePointLeft(6)).isEqualByComparingTo(decimal);
        }
        assertThatThrownBy(() -> new BigDecimal("0.0000001")
                .movePointRight(6).setScale(0, RoundingMode.UNNECESSARY))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void expansaoNaoAlteraIdentidadeItensETotaisDaEntradaConfirmada() {
        String schema = "it_micros_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrar(schema, "87");
            long fornecedor = jdbc.queryForObject("insert into " + schema
                    + ".fornecedor (nome,documento) values ('Legado','12345678901234') returning id", Long.class);
            long marca = jdbc.queryForObject("insert into " + schema
                    + ".marca (nome) values ('Marca') returning id", Long.class);
            long unidade = jdbc.queryForObject("insert into " + schema
                    + ".unidade_medida (nome,sigla) values ('Unidade','UN') returning id", Long.class);
            long produto = jdbc.queryForObject("insert into " + schema
                    + ".produto (nome,marca_id,unidade_medida_id) values ('Produto',?,?) returning id",
                    Long.class, marca, unidade);
            long nota = jdbc.queryForObject("""
                    insert into %s.nota_entrada
                        (numero,serie,modelo,fornecedor_id,valor_produtos,valor_total,situacao)
                    values ('000120','00A','55',?,12.34,12.34,'CONFIRMADA') returning id
                    """.formatted(schema), Long.class, fornecedor);
            jdbc.update("""
                    insert into %s.item_nota_entrada
                        (nota_entrada_id,produto_id,sequencia,quantidade,valor_unitario,valor_total)
                    values (?,?,1,1.125,10.97,12.34)
                    """.formatted(schema), nota, produto);
            long titulo = jdbc.queryForObject("""
                    insert into %s.conta_pagar
                        (fornecedor_id,nota_entrada_id,valor_original,valor_total,valor_pago,data_vencimento,situacao)
                    values (?,?,12.34,12.34,1.23,current_date,'PARCIAL') returning id
                    """.formatted(schema), Long.class, fornecedor, nota);
            jdbc.update("""
                    insert into %s.baixa_financeira (conta_pagar_id,valor,chave,saldo_anterior)
                    values (?,1.23,'anterior',true)
                    """.formatted(schema), titulo);
            jdbc.update("insert into " + schema + ".parcela_nota_entrada "
                    + "(nota_entrada_id,numero_parcela,valor,data_vencimento) values (?,1,12.34,current_date)", nota);
            String cabecalho = "select numero,serie,modelo,situacao,trim_scale(valor_total),updated_at from "
                    + schema + ".nota_entrada where id=?";
            String itens = "select trim_scale(quantidade) as quantidade,trim_scale(valor_unitario) as valor_unitario,"
                    + "trim_scale(valor_total) as valor_total,updated_at from "
                    + schema + ".item_nota_entrada where nota_entrada_id=?";
            var anterior = jdbc.queryForMap(cabecalho, nota);
            var itensAnteriores = jdbc.queryForList(itens, nota);

            migrar(schema, "95");

            assertThat(jdbc.queryForMap(cabecalho, nota)).isEqualTo(anterior);
            assertThat(jdbc.queryForList(itens, nota)).isEqualTo(itensAnteriores);
            assertThat(jdbc.queryForObject("select valor_total_micros from " + schema
                    + ".nota_entrada where id=?", BigDecimal.class, nota)).isNull();
            assertThat(jdbc.queryForObject("select valor from " + schema
                    + ".parcela_nota_entrada where nota_entrada_id=?", BigDecimal.class, nota))
                    .isEqualByComparingTo("12.34");
            assertThat(jdbc.queryForObject("select valor_micros from " + schema
                    + ".parcela_nota_entrada where nota_entrada_id=?", BigDecimal.class, nota)).isNull();
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns where table_schema=? and table_name='nota_entrada'
                    and column_name in ('numero','serie','modelo') and data_type='character varying'
                    """, Integer.class, schema)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select valor_pago from " + schema
                    + ".conta_pagar where id=?", BigDecimal.class, titulo)).isEqualByComparingTo("1.23");
            assertThat(jdbc.queryForObject("select valor from " + schema
                    + ".baixa_financeira where conta_pagar_id=?", BigDecimal.class, titulo)).isEqualByComparingTo("1.23");
            assertThat(jdbc.queryForObject("select valor_micros from " + schema
                    + ".baixa_financeira where conta_pagar_id=?", BigDecimal.class, titulo)).isNull();
            assertThat(jdbc.queryForObject("select " + schema + ".kaneko_micros(0.000001)",
                    BigDecimal.class)).isEqualTo(BigDecimal.ONE);
            assertThatThrownBy(() -> jdbc.queryForObject("select " + schema + ".kaneko_micros(0.0000001)",
                    BigDecimal.class)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("""
                    select numeric_scale from information_schema.columns
                    where table_schema=? and table_name='baixa_financeira' and column_name='valor'
                    """, Integer.class, schema)).isEqualTo(6);

            migrar(schema, "96");

            assertThat(jdbc.queryForMap(cabecalho.replace("trim_scale(valor_total)", "trim_scale(valor_total_micros * 0.000001)"), nota))
                    .isEqualTo(anterior);
            assertThat(jdbc.queryForList(itens.replace("trim_scale(quantidade)", "trim_scale(quantidade_micros * 0.000001)")
                    .replace("trim_scale(valor_unitario)", "trim_scale(valor_unitario_micros * 0.000001)")
                    .replace("trim_scale(valor_total)", "trim_scale(valor_total_micros * 0.000001)"), nota))
                    .isEqualTo(itensAnteriores);
            assertThat(jdbc.queryForObject("select valor_micros from " + schema
                    + ".parcela_nota_entrada where nota_entrada_id=?", BigDecimal.class, nota)).isEqualByComparingTo("12340000");
            assertThat(jdbc.queryForObject("select valor_pago_micros from " + schema
                    + ".conta_pagar where id=?", BigDecimal.class, titulo)).isEqualByComparingTo("1230000");
            assertThat(jdbc.queryForObject("select valor_micros from " + schema
                    + ".baixa_financeira where conta_pagar_id=?", BigDecimal.class, titulo)).isEqualByComparingTo("1230000");
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema=? and numeric_scale>0 and table_name in (
                        'nota_entrada', 'item_nota_entrada', 'parcela_nota_entrada',
                        'saldo_estoque', 'movimento_estoque', 'conta_pagar', 'baixa_financeira',
                        'importacao_nota_entrada', 'item_importacao_nota_entrada', 'parcela_importacao_nota')
                    """, Integer.class, schema)).isZero();
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema=? and table_name='produto'
                    and column_name in ('quantidade','quantidade_minima','valor_compra','custo')
                    """, Integer.class, schema)).isZero();
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema=? and numeric_scale=0 and (
                        (right(column_name,7)='_micros' and table_name in (
                            'nota_entrada', 'item_nota_entrada', 'parcela_nota_entrada', 'saldo_estoque',
                            'movimento_estoque', 'conta_pagar', 'baixa_financeira',
                            'importacao_nota_entrada', 'item_importacao_nota_entrada', 'parcela_importacao_nota'))
                        or (table_name='produto' and column_name in (
                            'quantidade_micros','quantidade_minima_micros','valor_compra_micros','custo_micros'))
                    )
                    """, Integer.class, schema)).isEqualTo(46);
        } finally {
            if (schema.matches("it_micros_[a-f0-9]{32}")) jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void migraColunasESincronizaNovasGravacoesSemAlterarHistoricoOuIdentidade() {
        String schema = "it_micros_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrar(schema, "83");
            long cliente = jdbc.queryForObject("insert into " + schema
                    + ".cliente (nome,documento) values ('Histórico','12345678901') returning id", Long.class);
            jdbc.update("""
                    insert into %s.nota_servico
                      (cliente_id, numero, serie, modelo, valor_servico, valor_total, aliquota_iss, valor_iss, situacao,
                        itens_conferidos, parcelas_conferidas, cronograma_definido)
                    values (?, '000120', '00A', 'NFS', 120, 120, 5.25, 6.30, 'EMITIDA',
                      '[{"sequencia":1,"quantidade":1.125,"valorUnitario":120,"valorDesconto":0,"valorTotal":120}]',
                      '[{"numeroParcela":1,"totalParcelas":1,"valor":120}]', true)
                    """.formatted(schema), cliente);
            jdbc.update("insert into " + schema
                    + ".condicao_pagamento (nome,percentual_juros) values ('Anterior',5.25)");
            migrar(schema, "87");
            assertThat(jdbc.queryForObject("select percentual_juros from " + schema
                    + ".condicao_pagamento where nome='Anterior'", BigDecimal.class)).isEqualByComparingTo("5.25");
            assertThat(jdbc.queryForObject("select percentual_juros_micros from " + schema
                    + ".condicao_pagamento where nome='Anterior'", BigDecimal.class)).isNull();
            var row = jdbc.queryForMap("select numero,serie,modelo,valor_total,aliquota_iss,situacao,"
                    + "(itens_conferidos->0->>'quantidade')::numeric qtd,"
                    + "(parcelas_conferidas->0->>'valor')::numeric parcela from " + schema + ".nota_servico");
            assertThat(row).containsEntry("numero", "000120").containsEntry("serie", "00A")
                    .containsEntry("modelo", "NFS").containsEntry("situacao", "EMITIDA");
            assertThat((BigDecimal) row.get("valor_total")).isEqualByComparingTo("120");
            assertThat((BigDecimal) row.get("aliquota_iss")).isEqualByComparingTo("5.25");
            assertThat((BigDecimal) row.get("qtd")).isEqualByComparingTo("1.125");
            assertThat((BigDecimal) row.get("parcela")).isEqualByComparingTo("120");
            var micros = jdbc.queryForMap("select valor_total_micros,aliquota_iss_micros from "
                    + schema + ".nota_servico");
            assertThat((BigDecimal) micros.get("valor_total_micros")).isEqualByComparingTo("120000000");
            assertThat((BigDecimal) micros.get("aliquota_iss_micros")).isEqualByComparingTo("5250000");
            assertThat(jdbc.queryForObject("""
                    select (1.125001::numeric * 1000000)::numeric(18,0)
                    """, BigDecimal.class)).isEqualByComparingTo("1125001");
            assertThat(jdbc.queryForObject("""
                    select count(*) from %1$s.nota_servico n
                    where n.valor_total=n.valor_total_micros*0.000001
                      and n.aliquota_iss=n.aliquota_iss_micros*0.000001
                    """.formatted(schema), Integer.class)).isEqualTo(1);
            // Esta etapa cobre as cinco colunas decimais do cabeçalho, não os demais módulos.
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns original
                    join information_schema.columns espelho
                      on espelho.table_schema=original.table_schema and espelho.table_name=original.table_name
                      and espelho.column_name=original.column_name||'_micros'
                      and espelho.numeric_scale=0 and espelho.is_generated='ALWAYS'
                      and espelho.numeric_precision=original.numeric_precision-original.numeric_scale+6
                    where original.table_schema=? and original.data_type='numeric' and original.numeric_scale>0
                      and original.table_name='nota_servico'
                    """, Integer.class, schema)).isEqualTo(5);
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema=? and table_name in ('nota_entrada','nota_saida','nota_servico')
                      and column_name in ('numero','serie','modelo') and data_type='character varying'
                    """, Integer.class, schema)).isEqualTo(9);
            assertThatThrownBy(() -> jdbc.update("update " + schema + ".nota_servico set aliquota_iss=101"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            long rascunho = jdbc.queryForObject("insert into " + schema
                    + ".nota_servico (cliente_id,numero,serie,modelo,aliquota_iss)"
                    + " values (?, '000121', '00A', 'NFS', 5.25) returning id", Long.class, cliente);
            String espelho = "select aliquota_iss_micros from " + schema + ".nota_servico where id=?";
            assertThat(jdbc.queryForObject(espelho, BigDecimal.class, rascunho)).isEqualByComparingTo("5250000");
            jdbc.update("update " + schema + ".nota_servico set aliquota_iss=7.5 where id=?", rascunho);
            assertThat(jdbc.queryForObject(espelho, BigDecimal.class, rascunho)).isEqualByComparingTo("7500000");

            var revisao = jdbc.queryForMap("select version, updated_at from " + schema
                    + ".nota_servico where numero='000120'");
            migrar(schema, "98");

            var migrada = jdbc.queryForMap("""
                    select numero, serie, modelo, situacao, valor_total_micros, aliquota_iss_micros,
                           (itens_conferidos->0->>'quantidade')::numeric quantidade_micros,
                           (itens_conferidos->0->>'valorTotal')::numeric item_micros,
                           (parcelas_conferidas->0->>'valor')::numeric parcela_micros
                    from %s.nota_servico where numero='000120'
                    """.formatted(schema));
            assertThat(migrada).containsEntry("numero", "000120").containsEntry("serie", "00A")
                    .containsEntry("modelo", "NFS").containsEntry("situacao", "EMITIDA");
            assertThat((BigDecimal) migrada.get("valor_total_micros")).isEqualByComparingTo("120000000");
            assertThat((BigDecimal) migrada.get("aliquota_iss_micros")).isEqualByComparingTo("5250000");
            assertThat((BigDecimal) migrada.get("quantidade_micros")).isEqualTo(new BigDecimal("1125000"));
            // O total de item histórico era 120, embora 1,125 × 120 resulte em 135.
            assertThat((BigDecimal) migrada.get("item_micros")).isEqualTo(new BigDecimal("120000000"));
            assertThat((BigDecimal) migrada.get("parcela_micros")).isEqualTo(new BigDecimal("120000000"));
            assertThat(jdbc.queryForMap("select version, updated_at from " + schema
                    + ".nota_servico where numero='000120'")).isEqualTo(revisao);
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns where table_schema=?
                    and table_name in ('nota_servico','ordem_servico','item_ordem_servico','servico')
                    and data_type='numeric' and numeric_scale>0
                    """, Integer.class, schema)).isZero();
        } finally {
            if (schema.matches("it_micros_[a-f0-9]{32}")) jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void cutoverSaidaPreservaNotaConfirmadaParcelasESaldoLegadoSemRecalcular() {
        String schema = "it_micros_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrar(schema, "96");
            long cliente = jdbc.queryForObject("insert into " + schema
                    + ".cliente (nome,documento) values ('Legado','12345678901') returning id", Long.class);
            long marca = jdbc.queryForObject("insert into " + schema
                    + ".marca (nome) values ('Marca') returning id", Long.class);
            long unidade = jdbc.queryForObject("insert into " + schema
                    + ".unidade_medida (nome,sigla) values ('Unidade','UN') returning id", Long.class);
            long produto = jdbc.queryForObject("insert into " + schema
                    + ".produto (nome,marca_id,unidade_medida_id) values ('Produto',?,?) returning id",
                    Long.class, marca, unidade);
            long nota = jdbc.queryForObject("""
                    insert into %s.nota_saida
                        (numero,serie,modelo,cliente_id,valor_produtos,valor_total,situacao,cronograma_definido)
                    values ('000120','00A','55',?,12.34,12.34,'CONFIRMADA',true) returning id
                    """.formatted(schema), Long.class, cliente);
            // O total histórico (12,34) não coincide com o produto recalculado a seis casas (12,341250).
            jdbc.update("""
                    insert into %s.item_nota_saida
                        (nota_saida_id,produto_id,sequencia,quantidade,valor_unitario,valor_total,custo_preco_final)
                    values (?,?,1,1.125,10.97,12.34,7.77)
                    """.formatted(schema), nota, produto);
            jdbc.update("insert into " + schema + ".parcela_nota_saida "
                    + "(nota_saida_id,numero_parcela,valor,data_vencimento) values (?,1,12.34,current_date)", nota);
            long titulo = jdbc.queryForObject("""
                    insert into %s.conta_receber
                        (cliente_id,nota_saida_id,valor_original,valor_total,valor_recebido,data_vencimento,situacao)
                    values (?,?,12.34,12.34,1.23,current_date,'PARCIAL') returning id
                    """.formatted(schema), Long.class, cliente, nota);
            jdbc.update("""
                    insert into %s.baixa_financeira (conta_receber_id,valor_micros,chave,saldo_anterior)
                    values (?,1230000,'anterior',true)
                    """.formatted(schema), titulo);

            String cabecalho = "select numero,serie,modelo,situacao,version,updated_at,trim_scale(valor_total) as total from "
                    + schema + ".nota_saida where id=?";
            String item = "select trim_scale(quantidade) as qtd,trim_scale(valor_unitario) as unit,"
                    + "trim_scale(valor_total) as total,trim_scale(custo_preco_final) as custo,updated_at from "
                    + schema + ".item_nota_saida where nota_saida_id=?";
            String conta = "select trim_scale(valor_recebido) as recebido,trim_scale(valor_total) as total,"
                    + "situacao,version,updated_at from " + schema + ".conta_receber where id=?";
            var notaAnterior = jdbc.queryForMap(cabecalho, nota);
            var itemAnterior = jdbc.queryForMap(item, nota);
            var contaAnterior = jdbc.queryForMap(conta, titulo);

            migrar(schema, "97");

            assertThat(jdbc.queryForMap(comMicros(cabecalho), nota)).isEqualTo(notaAnterior);
            assertThat(jdbc.queryForMap(comMicros(item), nota)).isEqualTo(itemAnterior);
            assertThat(jdbc.queryForMap(comMicros(conta), titulo)).isEqualTo(contaAnterior);
            assertThat(jdbc.queryForObject("select valor_micros from " + schema
                    + ".parcela_nota_saida where nota_saida_id=?", BigDecimal.class, nota)).isEqualByComparingTo("12340000");
            assertThat(jdbc.queryForObject("select valor_micros from " + schema
                    + ".baixa_financeira where conta_receber_id=? and saldo_anterior", BigDecimal.class, titulo))
                    .isEqualByComparingTo("1230000");
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema=? and table_name in ('nota_saida','item_nota_saida','parcela_nota_saida','conta_receber')
                      and numeric_scale=0 and right(column_name,7)='_micros' and is_generated='NEVER'
                    """, Integer.class, schema)).isEqualTo(22);
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema=? and table_name in ('nota_saida','item_nota_saida','parcela_nota_saida','conta_receber')
                      and numeric_scale>0
                    """, Integer.class, schema)).isZero();
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns where table_schema=? and table_name='nota_saida'
                      and column_name in ('numero','serie','modelo') and data_type='character varying'
                    """, Integer.class, schema)).isEqualTo(3);
        } finally {
            if (schema.matches("it_micros_[a-f0-9]{32}")) jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private static String comMicros(String consulta) {
        return consulta.replaceAll("trim_scale\\((\\w+)\\)", "trim_scale($1_micros * 0.000001)");
    }

    private void migrar(String schema, String versao) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration", "classpath:db/test-cutover")
                .target(MigrationVersion.fromVersion(versao)).load().migrate();
    }
}
