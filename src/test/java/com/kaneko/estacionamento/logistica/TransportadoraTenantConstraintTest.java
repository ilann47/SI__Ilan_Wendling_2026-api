package com.kaneko.estacionamento.logistica;

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
class TransportadoraTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private TransportadoraRepository transportadoras;

    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
    }

    @Test
    void documentoEUnicoSomenteDentroDaOrganizacao() {
        inserirTransportadora(tenantA.organizacaoId(), "12345678000190", null);
        inserirTransportadora(tenantB.organizacaoId(), "12345678000190", null);
        assertViolation(
                () -> inserirTransportadora(tenantA.organizacaoId(), "12345678000190", null),
                "23505", "uq_transportadora_organizacao_documento");
    }

    @Test
    void bancoRecusaCondicaoDeOutroTenant() {
        Long condicaoB = inserirCondicao(tenantB.organizacaoId(), "Condicao B");
        assertViolation(
                () -> inserirTransportadora(tenantA.organizacaoId(), "11111111000111", condicaoB),
                "23503", "fk_transportadora_condicao_organizacao");
    }

    @Test
    void triggerRecusaOwnerNuloParaCondicaoTenant() {
        Long condicaoB = inserirCondicao(tenantB.organizacaoId(), "Condicao B");
        assertViolation(
                () -> inserirTransportadora(null, "22222222000122", condicaoB),
                "23503", "fk_transportadora_condicao_organizacao");
    }

    @Test
    void historicoPermaneceSemOwnerEComUnicidadeLegada() {
        Long legado = inserirTransportadora(null, "33333333000133", null);
        assertThat(jdbc.queryForObject(
                "select organizacao_id from transportadora where id = ?", Long.class, legado)).isNull();
        assertViolation(
                () -> inserirTransportadora(null, "33333333000133", null),
                "23505", "uq_transportadora_legado_documento");
    }

    @Test
    void finderLegadoNaoResolveTransportadoraTenant() {
        Long tenantId = inserirTransportadora(tenantA.organizacaoId(), "44444444000144", null);
        Long legadoId = inserirTransportadora(null, "55555555000155", null);
        assertThat(transportadoras.findByIdAndOrganizacaoIdIsNull(tenantId)).isEmpty();
        assertThat(transportadoras.findByIdAndOrganizacaoIdIsNull(legadoId)).isPresent();
    }

    private Long inserirTransportadora(Long organizacaoId, String documento, Long condicaoId) {
        return jdbc.queryForObject("""
                INSERT INTO transportadora
                    (organizacao_id, nome, tipo, documento, condicao_pagamento_id,
                     ativo, created_at, updated_at)
                VALUES (?, 'Transportadora teste', 'JURIDICA', ?, ?, true, now(), now())
                RETURNING id
                """, Long.class, organizacaoId, documento, condicaoId);
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
