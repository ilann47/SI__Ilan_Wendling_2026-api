package com.kaneko.estacionamento.pagamento;

import static com.kaneko.estacionamento.support.database.PostgresConstraintAssertions.assertViolation;
import static org.assertj.core.api.Assertions.assertThat;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TenantApiIntegrationTest
class PagamentoTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;

    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
    }

    @Test
    void bancoPermiteMesmoNomeEmOrganizacoesDiferentes() {
        inserirForma(tenantA.organizacaoId(), "Pix");
        inserirForma(tenantB.organizacaoId(), "PIX");

        Integer total = jdbc.queryForObject(
                "select count(*) from forma_pagamento where lower(nome) = 'pix'", Integer.class);
        assertThat(total).isEqualTo(2);
    }

    @Test
    void bancoRecusaNomeNormalizadoDuplicadoNoMesmoTenant() {
        inserirForma(tenantA.organizacaoId(), "Pix");

        assertViolation(
                () -> inserirForma(tenantA.organizacaoId(), " PIX "),
                "23505",
                "uq_forma_pagamento_organizacao_nome");
    }

    @Test
    void bancoRecusaFormaDeOutroTenantNaParcela() {
        Long formaB = inserirForma(tenantB.organizacaoId(), "Cartao");
        Long condicaoA = inserirCondicao(tenantA.organizacaoId(), "A vista");

        assertViolation(
                () -> jdbc.update("""
                        INSERT INTO parcela_condicao_pagamento
                            (organizacao_id, condicao_pagamento_id, numero, dias,
                             percentual, forma_pagamento_id, created_at, updated_at)
                        VALUES (?, ?, 1, 0, 100.00, ?, now(), now())
                        """, tenantA.organizacaoId(), condicaoA, formaB),
                "23503",
                "fk_parcela_forma_organizacao");
    }

    @Test
    void bancoPreservaRegistroHistoricoSemOwner() {
        Long id = inserirForma(null, "Historica");

        Long organizacaoId = jdbc.queryForObject(
                "select organizacao_id from forma_pagamento where id = ?", Long.class, id);
        assertThat(organizacaoId).isNull();
    }

    private Long inserirForma(Long organizacaoId, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO forma_pagamento
                    (organizacao_id, nome, descricao, ativo, created_at, updated_at)
                VALUES (?, ?, 'Teste', true, now(), now())
                RETURNING id
                """, Long.class, organizacaoId, nome);
    }

    private Long inserirCondicao(Long organizacaoId, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO condicao_pagamento
                    (organizacao_id, nome, numero_parcelas, percentual_juros,
                     percentual_multa, percentual_desconto, ativo, created_at, updated_at)
                VALUES (?, ?, 1, 0, 0, 0, true, now(), now())
                RETURNING id
                """, Long.class, organizacaoId, nome);
    }
}
