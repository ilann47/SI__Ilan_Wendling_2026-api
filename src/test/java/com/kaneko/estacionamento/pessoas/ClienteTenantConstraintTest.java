package com.kaneko.estacionamento.pessoas;

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
class ClienteTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private ClienteRepository clientes;

    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
    }

    @Test
    void documentoEUnicoPorOrganizacao() {
        inserirCliente(tenantA.organizacaoId(), "12345678901", null);
        inserirCliente(tenantB.organizacaoId(), "12345678901", null);

        Integer total = jdbc.queryForObject(
                "select count(*) from cliente where documento = '12345678901'", Integer.class);
        assertThat(total).isEqualTo(2);
    }

    @Test
    void bancoRecusaDocumentoDuplicadoNoMesmoTenant() {
        inserirCliente(tenantA.organizacaoId(), "12345678901", null);

        assertViolation(
                () -> inserirCliente(tenantA.organizacaoId(), "12345678901", null),
                "23505",
                "uq_cliente_organizacao_documento");
    }

    @Test
    void bancoRecusaCondicaoDeOutroTenant() {
        Long condicaoB = inserirCondicao(tenantB.organizacaoId());

        assertViolation(
                () -> inserirCliente(tenantA.organizacaoId(), "99999999999", condicaoB),
                "23503",
                "fk_cliente_condicao_organizacao");
    }

    @Test
    void triggerRecusaClienteOwnerlessApontandoParaCondicaoTenant() {
        Long condicaoA = inserirCondicao(tenantA.organizacaoId());

        assertViolation(
                () -> inserirCliente(null, "55555555555", condicaoA),
                "23503",
                "fk_cliente_condicao_organizacao");
    }

    @Test
    void bancoPreservaHistoricoSemOwner() {
        Long clienteId = inserirCliente(null, "88888888888", null);

        Long organizacaoId = jdbc.queryForObject(
                "select organizacao_id from cliente where id = ?", Long.class, clienteId);
        assertThat(organizacaoId).isNull();
    }

    @Test
    void compatibilidadeLegadaNaoResolveClienteTenant() {
        Long clienteTenant = inserirCliente(
                tenantA.organizacaoId(), "77777777777", null);
        Long clienteLegado = inserirCliente(null, "66666666666", null);

        assertThat(clientes.findByIdAndOrganizacaoIdIsNull(clienteTenant)).isEmpty();
        assertThat(clientes.findByIdAndOrganizacaoIdIsNull(clienteLegado)).isPresent();
    }

    private Long inserirCliente(
            Long organizacaoId,
            String documento,
            Long condicaoPagamentoId) {
        return jdbc.queryForObject("""
                INSERT INTO cliente
                    (organizacao_id, nome, tipo, documento, condicao_pagamento_id,
                     limite_credito, ativo, created_at, updated_at)
                VALUES (?, 'Cliente teste', 'FISICA', ?, ?, 0, true, now(), now())
                RETURNING id
                """, Long.class, organizacaoId, documento, condicaoPagamentoId);
    }

    private Long inserirCondicao(Long organizacaoId) {
        return jdbc.queryForObject("""
                INSERT INTO condicao_pagamento
                    (organizacao_id, nome, numero_parcelas, percentual_juros,
                     percentual_multa, percentual_desconto, ativo, created_at, updated_at)
                VALUES (?, 'Condicao externa', 1, 0, 0, 0, true, now(), now())
                RETURNING id
                """, Long.class, organizacaoId);
    }
}
