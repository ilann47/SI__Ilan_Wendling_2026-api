package com.kaneko.estacionamento.fornecedor;

import static com.kaneko.estacionamento.support.database.PostgresConstraintAssertions.assertViolation;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TenantApiIntegrationTest
class FornecedorLegacyConsumerConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;

    private Long fornecedorTenant;

    @BeforeEach
    void setup() throws Exception {
        Long organizacaoId = tenant.criarTenantAdmin().organizacaoId();
        fornecedorTenant = inserirFornecedor(organizacaoId, "12345678000190");
    }

    @Test
    void notaEntradaGlobalNaoReferenciaFornecedorTenant() {
        assertViolation(() -> jdbc.update("""
                INSERT INTO nota_entrada
                    (numero, fornecedor_id, created_at, updated_at)
                VALUES ('N-1', ?, now(), now())
                """, fornecedorTenant), "23503", "fk_nota_entrada_fornecedor_owner");
    }

    @Test
    void contaPagarGlobalNaoReferenciaFornecedorTenant() {
        assertViolation(() -> jdbc.update("""
                INSERT INTO conta_pagar
                    (fornecedor_id, valor_original, valor_total, data_vencimento,
                     created_at, updated_at)
                VALUES (?, 10, 10, CURRENT_DATE, now(), now())
                """, fornecedorTenant), "23503", "fk_conta_pagar_fornecedor_owner");
    }

    @Test
    void contaPagarAvulsaGlobalNaoReferenciaFornecedorTenant() {
        assertViolation(() -> jdbc.update("""
                INSERT INTO contas_pagar_avulsa
                    (descricao, fornecedor_id, valor_original, valor_total,
                     data_vencimento, created_at, updated_at)
                VALUES ('Despesa', ?, 10, 10, CURRENT_DATE, now(), now())
                """, fornecedorTenant), "23503", "fk_contas_pagar_avulsa_fornecedor_owner");
    }

    @Test
    void produtoFornecedorGlobalNaoReferenciaFornecedorTenant() {
        Long produtoId = inserirProdutoHistorico();
        assertViolation(() -> jdbc.update("""
                INSERT INTO produto_fornecedor
                    (produto_id, fornecedor_id, ativo, created_at, updated_at)
                VALUES (?, ?, true, now(), now())
                """, produtoId, fornecedorTenant),
                "23503", "fk_produto_fornecedor_fornecedor_owner");
    }

    @Test
    void consumersHistoricosContinuamAceitandoFornecedorHistorico() {
        Long historico = inserirFornecedor(null, "99887766000155");
        assertThatCode(() -> jdbc.update("""
                INSERT INTO nota_entrada
                    (numero, fornecedor_id, created_at, updated_at)
                VALUES ('N-HIST', ?, now(), now())
                """, historico)).doesNotThrowAnyException();
    }

    @Test
    void backfillParcialDoFornecedorComDocumentoHistoricoERecusado() throws Exception {
        Long historico = inserirFornecedor(null, "11223344000155");
        jdbc.update("""
                INSERT INTO nota_entrada
                    (numero, fornecedor_id, created_at, updated_at)
                VALUES ('N-BACKFILL', ?, now(), now())
                """, historico);
        Long organizacaoId = tenant.criarTenantAdmin().organizacaoId();

        assertViolation(
                () -> jdbc.update(
                        "UPDATE fornecedor SET organizacao_id = ? WHERE id = ?",
                        organizacaoId, historico),
                "23514", "ck_fornecedor_backfill_dependencias");
    }

    @Test
    void bancoNormalizaDocumentoNaUnicidadeDoTenant() {
        assertViolation(
                () -> inserirFornecedor(
                        jdbc.queryForObject("select organizacao_id from fornecedor where id = ?",
                                Long.class, fornecedorTenant),
                        "12.345.678/0001-90"),
                "23505", "uq_fornecedor_organizacao_documento");
    }

    private Long inserirFornecedor(Long organizacaoId, String documento) {
        return jdbc.queryForObject("""
                INSERT INTO fornecedor
                    (organizacao_id, nome, tipo, documento, limite_credito,
                     ativo, created_at, updated_at)
                VALUES (?, 'Fornecedor teste', 'JURIDICA', ?, 0, true, now(), now())
                RETURNING id
                """, Long.class, organizacaoId, documento);
    }

    private Long inserirProdutoHistorico() {
        Long marca = jdbc.queryForObject("""
                INSERT INTO marca (nome, ativo, created_at, updated_at)
                VALUES ('Marca teste', true, now(), now()) RETURNING id
                """, Long.class);
        Long unidade = jdbc.queryForObject("""
                INSERT INTO unidade_medida (nome, sigla, ativo, created_at, updated_at)
                VALUES ('Unidade teste', 'UT', true, now(), now()) RETURNING id
                """, Long.class);
        return jdbc.queryForObject("""
                INSERT INTO produto
                    (nome, marca_id, unidade_medida_id, ativo, created_at, updated_at)
                VALUES ('Produto teste', ?, ?, true, now(), now()) RETURNING id
                """, Long.class, marca, unidade);
    }
}
