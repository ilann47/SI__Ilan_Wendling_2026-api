package com.kaneko.estacionamento.conveniencia;

import static com.kaneko.estacionamento.support.database.PostgresConstraintAssertions.assertViolation;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TenantApiIntegrationTest
class ProdutoFornecedorTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long produtoA;
    private Long produtoB;
    private Long fornecedorA;
    private Long fornecedorB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        produtoA = inserirProduto(tenantA.organizacaoId(), "A");
        produtoB = inserirProduto(tenantB.organizacaoId(), "B");
        fornecedorA = inserirFornecedor(tenantA.organizacaoId(), "A");
        fornecedorB = inserirFornecedor(tenantB.organizacaoId(), "B");
    }

    @Test
    void insertMesmoOwnerEPreservacaoDoParFuncionam() {
        assertThatCode(() -> inserirVinculo(
                tenantA.organizacaoId(), produtoA, fornecedorA)).doesNotThrowAnyException();
        assertViolation(() -> inserirVinculo(
                        tenantA.organizacaoId(), produtoA, fornecedorA),
                "23505", "uq_produto_fornecedor");
    }

    @Test
    void insertRecusaProdutoDeOutroTenant() {
        assertViolation(() -> inserirVinculo(
                        tenantA.organizacaoId(), produtoB, fornecedorA),
                "23503", "fk_produto_fornecedor_produto_organizacao");
    }

    @Test
    void insertRecusaFornecedorDeOutroTenant() {
        assertViolation(() -> inserirVinculo(
                        tenantA.organizacaoId(), produtoA, fornecedorB),
                "23503", "fk_produto_fornecedor_fornecedor_organizacao");
    }

    @Test
    void updateRecusaTrocaDeProduto() {
        Long id = inserirVinculo(tenantA.organizacaoId(), produtoA, fornecedorA);
        Long outroProdutoA = inserirProduto(tenantA.organizacaoId(), "A2");
        assertViolation(() -> jdbc.update(
                        "UPDATE produto_fornecedor SET produto_id=? WHERE id=?",
                        outroProdutoA, id),
                "23514", "ck_produto_fornecedor_identidade_imutavel");
    }

    @Test
    void updateRecusaTrocaDeFornecedor() {
        Long id = inserirVinculo(tenantA.organizacaoId(), produtoA, fornecedorA);
        Long outroFornecedorA = inserirFornecedor(tenantA.organizacaoId(), "A2");
        assertViolation(() -> jdbc.update(
                        "UPDATE produto_fornecedor SET fornecedor_id=? WHERE id=?",
                        outroFornecedorA, id),
                "23514", "ck_produto_fornecedor_identidade_imutavel");
    }

    @Test
    void updateRecusaTrocaDeOwner() {
        Long id = inserirVinculo(tenantA.organizacaoId(), produtoA, fornecedorA);
        assertViolation(() -> jdbc.update(
                        "UPDATE produto_fornecedor SET organizacao_id=? WHERE id=?",
                        tenantB.organizacaoId(), id),
                "23514", "ck_produto_fornecedor_owner_imutavel");
    }

    private Long inserirVinculo(Long org, Long produto, Long fornecedor) {
        return jdbc.queryForObject("""
                INSERT INTO produto_fornecedor
                    (organizacao_id,produto_id,fornecedor_id,ativo,created_at,updated_at)
                VALUES (?,?,?,true,now(),now()) RETURNING id
                """, Long.class, org, produto, fornecedor);
    }

    private Long inserirProduto(Long org, String sufixo) {
        Long marca = jdbc.queryForObject("""
                INSERT INTO marca (organizacao_id,nome,ativo,version,created_at,updated_at)
                VALUES (?,'Marca PFC ' || ?,true,0,now(),now()) RETURNING id
                """, Long.class, org, sufixo);
        Long unidade = jdbc.queryForObject("""
                INSERT INTO unidade_medida
                    (organizacao_id,nome,sigla,ativo,version,created_at,updated_at)
                VALUES (?,'Unidade PFC ' || ?,'U' || ?,true,0,now(),now()) RETURNING id
                """, Long.class, org, sufixo, sufixo);
        return jdbc.queryForObject("""
                INSERT INTO produto
                    (organizacao_id,nome,marca_id,unidade_medida_id,ativo,version,
                     created_at,updated_at)
                VALUES (?,'Produto PFC ' || ?,?,?,true,0,now(),now()) RETURNING id
                """, Long.class, org, sufixo, marca, unidade);
    }

    private Long inserirFornecedor(Long org, String sufixo) {
        return jdbc.queryForObject("""
                INSERT INTO fornecedor
                    (organizacao_id,nome,tipo,documento,limite_credito,ativo,
                     created_at,updated_at)
                VALUES (?,'Fornecedor PFC ' || ?,'JURIDICA',?,0,true,now(),now())
                RETURNING id
                """, Long.class, org, sufixo, "54" + Math.abs((org + sufixo).hashCode()));
    }
}
