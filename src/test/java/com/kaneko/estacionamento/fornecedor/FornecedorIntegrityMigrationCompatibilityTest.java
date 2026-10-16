package com.kaneko.estacionamento.fornecedor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static com.kaneko.estacionamento.support.database.PostgresConstraintAssertions.assertViolation;

import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class FornecedorIntegrityMigrationCompatibilityTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    private String schema;

    @AfterEach
    void limpar() {
        if (schema != null && schema.matches("it_supplier_integrity_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void v47RecusaNotaEntradaPreexistenteComFornecedorTenant() {
        Long fornecedorId = prepararFornecedorTenantAteV43();
        jdbc.update("""
                INSERT INTO %s.nota_entrada
                    (numero, fornecedor_id, created_at, updated_at)
                VALUES ('N-PREFLIGHT', ?, now(), now())
                """.formatted(schema), fornecedorId);

        migrarAteV46EEsperarFalhaV47("fk_nota_entrada_fornecedor_owner");
    }

    @Test
    void v47RecusaContaPagarPreexistenteComFornecedorTenant() {
        Long fornecedorId = prepararFornecedorTenantAteV43();
        jdbc.update("""
                INSERT INTO %s.conta_pagar
                    (fornecedor_id, valor_original, valor_total, data_vencimento,
                     created_at, updated_at)
                VALUES (?, 10, 10, CURRENT_DATE, now(), now())
                """.formatted(schema), fornecedorId);

        migrarAteV46EEsperarFalhaV47("fk_conta_pagar_fornecedor_owner");
    }

    @Test
    void v47RecusaContaPagarAvulsaPreexistenteComFornecedorTenant() {
        Long fornecedorId = prepararFornecedorTenantAteV43();
        jdbc.update("""
                INSERT INTO %s.contas_pagar_avulsa
                    (descricao, fornecedor_id, valor_original, valor_total,
                     data_vencimento, created_at, updated_at)
                VALUES ('PREFLIGHT', ?, 10, 10, CURRENT_DATE, now(), now())
                """.formatted(schema), fornecedorId);

        migrarAteV46EEsperarFalhaV47("fk_contas_pagar_avulsa_fornecedor_owner");
    }

    @Test
    void v47RecusaProdutoFornecedorPreexistenteComFornecedorTenant() {
        Long fornecedorId = prepararFornecedorTenantAteV43();
        Long produtoId = inserirProdutoHistorico();
        jdbc.update("""
                INSERT INTO %s.produto_fornecedor
                    (produto_id, fornecedor_id, ativo, created_at, updated_at)
                VALUES (?, ?, true, now(), now())
                """.formatted(schema), produtoId, fornecedorId);

        migrarAteV46EEsperarFalhaV47("fk_produto_fornecedor_fornecedor_owner");
    }

    @Test
    void v47RecusaCondicaoTenantPreexistenteEmFornecedorHistorico() {
        GrafoHistorico grafo = prepararGrafoHistoricoAteV43();
        jdbc.update("UPDATE " + schema + ".condicao_pagamento SET organizacao_id = ? WHERE id = ?",
                grafo.organizacaoId(), grafo.condicaoId());

        migrarAteV46EEsperarFalhaV47("fk_fornecedor_condicao_organizacao");
    }

    @Test
    void v47RecusaTransportadoraTenantPreexistenteEmFornecedorHistorico() {
        GrafoHistorico grafo = prepararGrafoHistoricoAteV43();
        jdbc.update("UPDATE " + schema + ".transportadora SET organizacao_id = ? WHERE id = ?",
                grafo.organizacaoId(), grafo.transportadoraId());

        migrarAteV46EEsperarFalhaV47("fk_fornecedor_transportadora_organizacao");
    }

    @Test
    void v47PreservaGrafoHistoricoValidoEAplicaVersao() {
        GrafoHistorico grafo = prepararGrafoHistoricoAteV43();
        Long fornecedorId = grafo.fornecedorId();
        Long produtoId = inserirProdutoHistorico();
        jdbc.update("""
                INSERT INTO %s.nota_entrada
                    (numero, fornecedor_id, created_at, updated_at)
                VALUES ('N-VALIDA', ?, now(), now())
                """.formatted(schema), fornecedorId);
        jdbc.update("""
                INSERT INTO %s.conta_pagar
                    (fornecedor_id, valor_original, valor_total, data_vencimento,
                     created_at, updated_at)
                VALUES (?, 10, 10, CURRENT_DATE, now(), now())
                """.formatted(schema), fornecedorId);
        jdbc.update("""
                INSERT INTO %s.contas_pagar_avulsa
                    (descricao, fornecedor_id, valor_original, valor_total,
                     data_vencimento, created_at, updated_at)
                VALUES ('VALIDA', ?, 10, 10, CURRENT_DATE, now(), now())
                """.formatted(schema), fornecedorId);
        jdbc.update("""
                INSERT INTO %s.produto_fornecedor
                    (produto_id, fornecedor_id, ativo, created_at, updated_at)
                VALUES (?, ?, true, now(), now())
                """.formatted(schema), produtoId, fornecedorId);

        flywayAte("47").migrate();

        assertThat(flywayAte("47").info().current().getVersion().getVersion()).isEqualTo("47");
        assertThat(jdbc.queryForObject(
                "SELECT organizacao_id FROM " + schema + ".fornecedor WHERE id = ?",
                Long.class, fornecedorId)).isNull();
        assertViolation(
                () -> jdbc.update(
                        "UPDATE " + schema + ".fornecedor SET organizacao_id = ? WHERE id = ?",
                        grafo.organizacaoId(), fornecedorId),
                "23514", "ck_fornecedor_owner_imutavel");
    }

    private Long prepararFornecedorTenantAteV43() {
        prepararSchemaAteV43();
        Long organizacaoId = inserirOrganizacao();
        return inserirFornecedor(organizacaoId, "12345678000190", null, null);
    }

    private void prepararSchemaAteV43() {
        schema = "it_supplier_integrity_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("43").migrate();
    }

    private GrafoHistorico prepararGrafoHistoricoAteV43() {
        schema = "it_supplier_integrity_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        flywayAte("37").migrate();
        Long organizacaoId = inserirOrganizacao();
        Long condicaoId = inserirCondicaoHistorica();
        Long transportadoraId = inserirTransportadoraHistorica();
        Long fornecedorId = inserirFornecedorAntesDoOwnership(
                "88776655000133", condicaoId, transportadoraId);
        flywayAte("43").migrate();
        return new GrafoHistorico(
                organizacaoId, condicaoId, transportadoraId, fornecedorId);
    }

    private void migrarAteV46EEsperarFalhaV47(String constraint) {
        flywayAte("46").migrate();
        Throwable falha = catchThrowable(() -> flywayAte("47").migrate());
        assertThat(falha).as("a V47 deve recusar o grafo preexistente invalido").isNotNull();
        SQLException sql = causaSql(falha);
        assertThat((Object) sql).isNotNull();
        assertThat(sql.getSQLState()).isEqualTo("23503");
        assertThat(mensagens(falha)).contains(constraint.toLowerCase(Locale.ROOT));
        assertThat(flywayAte("47").info().current().getVersion().getVersion()).isEqualTo("46");
    }

    private Long inserirOrganizacao() {
        return jdbc.queryForObject("""
                INSERT INTO %s.organizacao
                    (documento, razao_social, moeda, fuso_horario, regiao, plano,
                     status, version, created_at, updated_at)
                VALUES ('77889966000155', 'Tenant supplier integrity', 'BRL',
                        'America/Sao_Paulo', 'BR-PR', 'ENTERPRISE', 'ATIVA', 0,
                        now(), now()) RETURNING id
                """.formatted(schema), Long.class);
    }

    private Long inserirCondicaoHistorica() {
        return jdbc.queryForObject("""
                INSERT INTO %s.condicao_pagamento
                    (nome, numero_parcelas, percentual_juros, percentual_multa,
                     percentual_desconto, ativo, created_at, updated_at)
                VALUES ('Condicao supplier integrity', 1, 0, 0, 0, true, now(), now())
                RETURNING id
                """.formatted(schema), Long.class);
    }

    private Long inserirTransportadoraHistorica() {
        return jdbc.queryForObject("""
                INSERT INTO %s.transportadora
                    (nome, tipo, documento, ativo, created_at, updated_at)
                VALUES ('Transportadora supplier integrity', 'JURIDICA',
                        '88990011000122', true, now(), now()) RETURNING id
                """.formatted(schema), Long.class);
    }

    private Long inserirFornecedor(
            Long organizacaoId, String documento, Long condicaoId, Long transportadoraId) {
        return jdbc.queryForObject("""
                INSERT INTO %s.fornecedor
                    (organizacao_id, nome, tipo, documento, condicao_pagamento_id,
                     transportadora_id, limite_credito, ativo, created_at, updated_at)
                VALUES (?, 'Fornecedor supplier integrity', 'JURIDICA', ?, ?, ?,
                        0, true, now(), now()) RETURNING id
                """.formatted(schema), Long.class,
                organizacaoId, documento, condicaoId, transportadoraId);
    }

    private Long inserirFornecedorAntesDoOwnership(
            String documento, Long condicaoId, Long transportadoraId) {
        return jdbc.queryForObject("""
                INSERT INTO %s.fornecedor
                    (nome, tipo, documento, condicao_pagamento_id,
                     transportadora_id, limite_credito, ativo, created_at, updated_at)
                VALUES ('Fornecedor supplier integrity', 'JURIDICA', ?, ?, ?,
                        0, true, now(), now()) RETURNING id
                """.formatted(schema), Long.class, documento, condicaoId, transportadoraId);
    }

    private Long inserirProdutoHistorico() {
        Long marcaId = jdbc.queryForObject("""
                INSERT INTO %s.marca (nome, ativo, created_at, updated_at)
                VALUES ('Marca supplier integrity', true, now(), now()) RETURNING id
                """.formatted(schema), Long.class);
        Long unidadeId = jdbc.queryForObject("""
                INSERT INTO %s.unidade_medida (nome, sigla, ativo, created_at, updated_at)
                VALUES ('Unidade supplier integrity', 'USI', true, now(), now()) RETURNING id
                """.formatted(schema), Long.class);
        return jdbc.queryForObject("""
                INSERT INTO %s.produto
                    (nome, marca_id, unidade_medida_id, ativo, created_at, updated_at)
                VALUES ('Produto supplier integrity', ?, ?, true, now(), now()) RETURNING id
                """.formatted(schema), Long.class, marcaId, unidadeId);
    }

    private Flyway flywayAte(String versao) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(versao)).load();
    }

    private static SQLException causaSql(Throwable falha) {
        Throwable atual = falha;
        while (atual != null) {
            if (atual instanceof SQLException sql) {
                return sql;
            }
            atual = atual.getCause();
        }
        return null;
    }

    private static String mensagens(Throwable falha) {
        StringBuilder mensagens = new StringBuilder();
        Throwable atual = falha;
        while (atual != null) {
            if (atual.getMessage() != null) {
                mensagens.append(' ').append(atual.getMessage());
            }
            atual = atual.getCause();
        }
        return mensagens.toString().toLowerCase(Locale.ROOT);
    }

    private record GrafoHistorico(
            Long organizacaoId,
            Long condicaoId,
            Long transportadoraId,
            Long fornecedorId) {
    }
}
