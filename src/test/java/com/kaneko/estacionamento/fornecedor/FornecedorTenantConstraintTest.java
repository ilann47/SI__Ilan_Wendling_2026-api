package com.kaneko.estacionamento.fornecedor;

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
class FornecedorTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private FornecedorRepository fornecedores;

    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
    }

    @Test
    void documentoEUnicoSomenteNoTenant() {
        inserirFornecedor(tenantA.organizacaoId(), "12345678000190", null, null);
        inserirFornecedor(tenantB.organizacaoId(), "12345678000190", null, null);
        assertViolation(
                () -> inserirFornecedor(tenantA.organizacaoId(), "12345678000190", null, null),
                "23505", "uq_fornecedor_organizacao_documento");
    }

    @Test
    void recusaCondicaoCrossTenantEOwnerless() {
        Long condicaoB = inserirCondicao(tenantB.organizacaoId());
        assertViolation(
                () -> inserirFornecedor(tenantA.organizacaoId(), "11111111000111", condicaoB, null),
                "23503", "fk_fornecedor_condicao_organizacao");
    }

    @Test
    void triggerRecusaCondicaoTenantEmFornecedorOwnerless() {
        Long condicaoB = inserirCondicao(tenantB.organizacaoId());
        assertViolation(
                () -> inserirFornecedor(null, "22222222000122", condicaoB, null),
                "23503", "fk_fornecedor_condicao_organizacao");
    }

    @Test
    void recusaTransportadoraCrossTenantEOwnerless() {
        Long transportadoraB = inserirTransportadora(tenantB.organizacaoId());
        assertViolation(
                () -> inserirFornecedor(tenantA.organizacaoId(), "33333333000133", null, transportadoraB),
                "23503", "fk_fornecedor_transportadora_organizacao");
    }

    @Test
    void triggerRecusaTransportadoraTenantEmFornecedorOwnerless() {
        Long transportadoraB = inserirTransportadora(tenantB.organizacaoId());
        assertViolation(
                () -> inserirFornecedor(null, "44444444000144", null, transportadoraB),
                "23503", "fk_fornecedor_transportadora_organizacao");
    }

    @Test
    void preservaHistoricoEFinderLegadoNaoResolveTenant() {
        Long tenantId = inserirFornecedor(tenantA.organizacaoId(), "55555555000155", null, null);
        Long legadoId = inserirFornecedor(null, "66666666000166", null, null);
        assertThat(fornecedores.findByIdAndOrganizacaoIdIsNull(tenantId)).isEmpty();
        assertThat(fornecedores.findByIdAndOrganizacaoIdIsNull(legadoId)).isPresent();
        assertViolation(
                () -> inserirFornecedor(null, "66666666000166", null, null),
                "23505", "uq_fornecedor_legado_documento");
    }

    private Long inserirFornecedor(Long organizacaoId, String documento, Long condicaoId, Long transportadoraId) {
        return jdbc.queryForObject("""
                INSERT INTO fornecedor
                    (organizacao_id, nome, tipo, documento, condicao_pagamento_id,
                     transportadora_id, limite_credito, ativo, created_at, updated_at)
                VALUES (?, 'Fornecedor teste', 'JURIDICA', ?, ?, ?, 0, true, now(), now())
                RETURNING id
                """, Long.class, organizacaoId, documento, condicaoId, transportadoraId);
    }

    private Long inserirCondicao(Long organizacaoId) {
        return jdbc.queryForObject("""
                INSERT INTO condicao_pagamento
                    (organizacao_id, nome, numero_parcelas, percentual_juros,
                     percentual_multa, percentual_desconto, ativo, created_at, updated_at)
                VALUES (?, 'Condicao teste', 1, 0, 0, 0, true, now(), now()) RETURNING id
                """, Long.class, organizacaoId);
    }

    private Long inserirTransportadora(Long organizacaoId) {
        return jdbc.queryForObject("""
                INSERT INTO transportadora
                    (organizacao_id, nome, tipo, documento, ativo, created_at, updated_at)
                VALUES (?, 'Transportadora teste', 'JURIDICA', ?, true, now(), now()) RETURNING id
                """, Long.class, organizacaoId, "88" + organizacaoId + "000188");
    }
}
