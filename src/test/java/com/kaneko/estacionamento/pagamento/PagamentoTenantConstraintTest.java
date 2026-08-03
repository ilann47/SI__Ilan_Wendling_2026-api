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
    @Autowired private FormaPagamentoRepository formas;
    @Autowired private CondicaoPagamentoRepository condicoes;

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
        inserirCondicao(tenantA.organizacaoId(), "A vista");
        inserirCondicao(tenantB.organizacaoId(), " A VISTA ");

        Integer total = jdbc.queryForObject(
                "select count(*) from forma_pagamento where lower(nome) = 'pix'", Integer.class);
        assertThat(total).isEqualTo(2);
        Integer condicoes = jdbc.queryForObject(
                "select count(*) from condicao_pagamento where lower(btrim(nome)) = 'a vista'",
                Integer.class);
        assertThat(condicoes).isEqualTo(2);
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
    void bancoRecusaCondicaoNormalizadaDuplicadaNoMesmoTenant() {
        inserirCondicao(tenantA.organizacaoId(), "A vista");

        assertViolation(
                () -> inserirCondicao(tenantA.organizacaoId(), " A VISTA "),
                "23505",
                "uq_condicao_pagamento_organizacao_nome");
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
    void bancoRecusaCondicaoDeOutroTenantNaParcela() {
        Long condicaoA = inserirCondicao(tenantA.organizacaoId(), "Condicao A");

        assertViolation(
                () -> inserirParcela(tenantB.organizacaoId(), condicaoA, null),
                "23503",
                "fk_parcela_condicao_organizacao");
    }

    @Test
    void triggerRecusaParcelaOwnerlessApontandoParaCatalogoTenant() {
        Long condicaoLegada = inserirCondicao(null, "Condicao legada");
        Long formaA = inserirForma(tenantA.organizacaoId(), "Forma A");

        assertViolation(
                () -> inserirParcela(null, condicaoLegada, formaA),
                "23503",
                "fk_parcela_forma_organizacao");
    }

    @Test
    void legadoMantemUnicidadeAnterior() {
        inserirForma(null, "Dinheiro legado");
        assertViolation(
                () -> inserirForma(null, "Dinheiro legado"),
                "23505",
                "uq_forma_pagamento_legado_nome");
    }

    @Test
    void compatibilidadeLegadaNaoResolveCatalogoTenant() {
        Long formaTenant = inserirForma(tenantA.organizacaoId(), "Forma tenant");
        Long formaLegada = inserirForma(null, "Forma legada");
        Long condicaoTenant = inserirCondicao(tenantA.organizacaoId(), "Condicao tenant");
        Long condicaoLegada = inserirCondicao(null, "Condicao legada");

        assertThat(formas.findByIdAndOrganizacaoIdIsNull(formaTenant)).isEmpty();
        assertThat(formas.findByIdAndOrganizacaoIdIsNull(formaLegada)).isPresent();
        assertThat(condicoes.findByIdAndOrganizacaoIdIsNull(condicaoTenant)).isEmpty();
        assertThat(condicoes.findByIdAndOrganizacaoIdIsNull(condicaoLegada)).isPresent();
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

    private void inserirParcela(
            Long organizacaoId,
            Long condicaoPagamentoId,
            Long formaPagamentoId) {
        jdbc.update("""
                INSERT INTO parcela_condicao_pagamento
                    (organizacao_id, condicao_pagamento_id, numero, dias,
                     percentual, forma_pagamento_id, created_at, updated_at)
                VALUES (?, ?, 1, 0, 100.00, ?, now(), now())
                """, organizacaoId, condicaoPagamentoId, formaPagamentoId);
    }
}
