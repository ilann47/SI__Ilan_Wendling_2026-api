package com.kaneko.estacionamento.estoque;

import static com.kaneko.estacionamento.support.database.PostgresConstraintAssertions.assertViolation;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TenantApiIntegrationTest
class EstoqueTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private Long produtoA;
    private Long produtoB;
    private Long localA;
    private Long localB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
        produtoA = inserirProduto(tenantA.organizacaoId(), "A");
        produtoB = inserirProduto(tenantB.organizacaoId(), "B");
        localA = inserirLocal(tenantA.organizacaoId(), "A");
        localB = inserirLocal(tenantB.organizacaoId(), "B");
    }

    @Test
    void saldoRecusaProdutoDeOutroTenant() {
        assertViolation(() -> inserirSaldo(tenantA.organizacaoId(), produtoB, localA, "0"),
                "23503", "fk_saldo_estoque_produto_organizacao");
    }

    @Test
    void saldoRecusaLocalDeOutroTenant() {
        assertViolation(() -> inserirSaldo(tenantA.organizacaoId(), produtoA, localB, "0"),
                "23503", "fk_saldo_estoque_local_organizacao");
    }

    @Test
    void saldoNuncaPodeFicarNegativo() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "0");
        assertViolation(() -> jdbc.update(
                        "UPDATE saldo_estoque SET quantidade=-1 WHERE id=?", saldo),
                "23514", "ck_saldo_estoque_quantidade");
    }

    @Test
    void saldoRecusaAlteracaoDiretaSemMovimentoCorrespondente() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "0");
        assertViolation(() -> {
            jdbc.update("UPDATE saldo_estoque SET quantidade=1 WHERE id=?", saldo);
            validarRazaoDiferido();
        }, "23514", "ck_saldo_estoque_razao_consistente");
    }

    @Test
    void razaoRecusaMovimentoDesalinhadoComSaldoFinal() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "2");
        inserirMovimento(tenantA, produtoA, localA, saldo,
                "AJUSTE", "1", "0", "1", "razao-final", null);
        assertViolation(this::validarRazaoDiferido,
                "23514", "ck_saldo_estoque_razao_consistente");
    }

    @Test
    void razaoRecusaQuebraNaCadeiaDeMovimentos() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "2");
        inserirMovimento(tenantA, produtoA, localA, saldo,
                "AJUSTE", "1", "0", "1", "razao-cadeia-1", null);
        inserirMovimento(tenantA, produtoA, localA, saldo,
                "AJUSTE", "1", "0", "1", "razao-cadeia-2", null);
        assertViolation(this::validarRazaoDiferido,
                "23514", "ck_saldo_estoque_razao_consistente");
    }

    @Test
    void movimentoValidaAritmetica() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "2");
        assertViolation(() -> inserirMovimento(tenantA, produtoA, localA, saldo,
                        "AJUSTE", "1", "2", "9", "aritmetica", null),
                "23514", "ck_movimento_estoque_saldos");
    }

    @Test
    void recebimentoExigeDeltaPositivo() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "2");
        assertViolation(() -> inserirMovimento(tenantA, produtoA, localA, saldo,
                        "RECEBIMENTO", "-1", "2", "1", "sinal", null),
                "23514", "ck_movimento_estoque_sinal");
    }

    @Test
    void movimentoEAppendOnly() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "2");
        Long movimento = inserirMovimento(tenantA, produtoA, localA, saldo,
                "AJUSTE", "2", "0", "2", "imutavel", null);
        assertViolation(() -> jdbc.update(
                        "UPDATE movimento_estoque SET motivo='alterado' WHERE id=?", movimento),
                "23514", "ck_movimento_estoque_append_only");
    }

    @Test
    void origemEUnicaIndependentementeDoAtor() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "2");
        inserirMovimento(tenantA, produtoA, localA, saldo,
                "AJUSTE", "1", "0", "1", "origem-unica", null);
        assertViolation(() -> inserirMovimento(tenantA, produtoA, localA, saldo,
                        "AJUSTE", "1", "1", "2", "origem-unica", null),
                "23505", "uq_movimento_estoque_origem");
    }

    @Test
    void compensacaoExigeMesmasPontasDoMovimentoOriginal() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "2");
        Long original = inserirMovimento(tenantA, produtoA, localA, saldo,
                "AJUSTE", "2", "0", "2", "original", null);
        Long outroProduto = inserirProduto(tenantA.organizacaoId(), "A2");
        Long outroLocal = inserirLocal(tenantA.organizacaoId(), "A2");
        Long outroSaldo = inserirSaldo(tenantA.organizacaoId(), outroProduto, outroLocal, "2");
        assertViolation(() -> inserirMovimento(tenantA, outroProduto, outroLocal, outroSaldo,
                        "COMPENSACAO", "-2", "2", "0", "comp-cross", original),
                "23514", "ck_movimento_estoque_compensacao_valida");
    }

    @Test
    void compensacaoExigeDeltaExatamenteInverso() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "1");
        Long original = inserirMovimento(tenantA, produtoA, localA, saldo,
                "AJUSTE", "2", "0", "2", "comp-delta-original", null);
        assertViolation(() -> inserirMovimento(tenantA, produtoA, localA, saldo,
                        "COMPENSACAO", "-1", "2", "1", "comp-delta-invalido", original),
                "23514", "ck_movimento_estoque_compensacao_valida");
    }

    @Test
    void compensacaoNaoPodeCompensarOutraCompensacao() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "0");
        Long original = inserirMovimento(tenantA, produtoA, localA, saldo,
                "AJUSTE", "2", "0", "2", "comp-tipo-original", null);
        Long compensacao = inserirMovimento(tenantA, produtoA, localA, saldo,
                "COMPENSACAO", "-2", "2", "0", "comp-tipo-primeira", original);
        assertViolation(() -> inserirMovimento(tenantA, produtoA, localA, saldo,
                        "COMPENSACAO", "2", "0", "2", "comp-tipo-segunda", compensacao),
                "23514", "ck_movimento_estoque_compensacao_valida");
    }

    @Test
    void compensacaoNaoPodeReferenciarASiPropria() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "0");
        Long movimento = jdbc.queryForObject(
                "SELECT nextval(pg_get_serial_sequence('movimento_estoque','id'))",
                Long.class);
        assertViolation(() -> jdbc.update("""
                        INSERT INTO movimento_estoque
                            (id,organizacao_id,produto_id,local_estoque_id,saldo_estoque_id,
                             tipo,delta,saldo_anterior,saldo_posterior,origem_tipo,
                             origem_chave,compensado_id,ator_id,motivo,ocorrido_em,created_at)
                        VALUES (?,?,?,?,?,'COMPENSACAO',-1,1,0,'TESTE',?,?,?,
                                'Auto referencia',now(),now())
                        """, movimento, tenantA.organizacaoId(), produtoA, localA, saldo,
                        "comp-auto", movimento, tenantA.usuarioId()),
                "23514", "ck_movimento_estoque_compensacao_valida");
    }

    @Test
    void produtoTenantNaoAceitaSaldoLegado() {
        assertViolation(() -> jdbc.update(
                        "UPDATE produto SET quantidade=1 WHERE id=?", produtoA),
                "23514", "ck_estoque_produto_tenant_sem_saldo_legado");
    }

    @Test
    void produtoComSaldoNaoZeroNaoPodeSerInativado() {
        Long saldo = inserirSaldo(tenantA.organizacaoId(), produtoA, localA, "2");
        inserirMovimento(tenantA, produtoA, localA, saldo,
                "AJUSTE", "2", "0", "2", "produto-inativacao", null);
        assertViolation(() -> jdbc.update(
                        "UPDATE produto SET ativo=false WHERE id=?", produtoA),
                "23514", "ck_estoque_produto_inativo_com_saldo");
    }

    private void validarRazaoDiferido() {
        jdbc.execute("SET CONSTRAINTS ck_saldo_estoque_razao_consistente_saldo, "
                + "ck_saldo_estoque_razao_consistente_movimento IMMEDIATE");
    }

    private Long inserirLocal(Long org, String sufixo) {
        return jdbc.queryForObject("""
                INSERT INTO local_estoque (organizacao_id,nome,ativo,version,created_at,updated_at)
                VALUES (?,'Local Constraint ' || ?,true,0,now(),now()) RETURNING id
                """, Long.class, org, sufixo);
    }

    private Long inserirSaldo(Long org, Long produto, Long local, String quantidade) {
        return jdbc.queryForObject("""
                INSERT INTO saldo_estoque
                    (organizacao_id,produto_id,local_estoque_id,quantidade,version,
                     created_at,updated_at)
                VALUES (?,?,?,?,0,now(),now()) RETURNING id
                """, Long.class, org, produto, local, new BigDecimal(quantidade));
    }

    private Long inserirMovimento(TenantFixture fixture, Long produto, Long local, Long saldo,
            String tipo, String delta, String anterior, String posterior,
            String origem, Long compensado) {
        return jdbc.queryForObject("""
                INSERT INTO movimento_estoque
                    (organizacao_id,produto_id,local_estoque_id,saldo_estoque_id,tipo,
                     delta,saldo_anterior,saldo_posterior,origem_tipo,origem_chave,
                     compensado_id,ator_id,motivo,ocorrido_em,created_at)
                VALUES (?,?,?,?,?,?,?,?, 'TESTE', ?,?,?, 'Motivo teste',now(),now())
                RETURNING id
                """, Long.class, fixture.organizacaoId(), produto, local, saldo, tipo,
                new BigDecimal(delta), new BigDecimal(anterior), new BigDecimal(posterior),
                origem, compensado, fixture.usuarioId());
    }

    private Long inserirProduto(Long org, String sufixo) {
        Long marca = jdbc.queryForObject("""
                INSERT INTO marca (organizacao_id,nome,ativo,version,created_at,updated_at)
                VALUES (?,'Marca Estoque Constraint ' || ?,true,0,now(),now()) RETURNING id
                """, Long.class, org, sufixo);
        Long unidade = jdbc.queryForObject("""
                INSERT INTO unidade_medida
                    (organizacao_id,nome,sigla,ativo,version,created_at,updated_at)
                VALUES (?,'Unidade Estoque Constraint ' || ?,'EC' || ?,true,0,now(),now())
                RETURNING id
                """, Long.class, org, sufixo, sufixo);
        return jdbc.queryForObject("""
                INSERT INTO produto
                    (organizacao_id,nome,marca_id,unidade_medida_id,quantidade_minima,
                     ativo,version,created_at,updated_at)
                VALUES (?,'Produto Estoque Constraint ' || ?,?,?,1,true,0,now(),now())
                RETURNING id
                """, Long.class, org, sufixo, marca, unidade);
    }
}
