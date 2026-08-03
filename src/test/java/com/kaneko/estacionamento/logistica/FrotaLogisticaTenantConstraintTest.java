package com.kaneko.estacionamento.logistica;

import static com.kaneko.estacionamento.support.database.PostgresConstraintAssertions.assertViolation;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TenantApiIntegrationTest
class FrotaLogisticaTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    private TenantFixture a;
    private TenantFixture b;
    private Long transportadoraA;
    private Long transportadoraB;
    private Long veiculoA;
    private Long veiculoB;

    @BeforeEach
    void setup() throws Exception {
        a = tenant.criarTenantAdmin();
        b = tenant.criarTenantAdmin();
        transportadoraA = transportadora(a.organizacaoId(), "T A", "44111111000111");
        transportadoraB = transportadora(b.organizacaoId(), "T B", "44222222000122");
        veiculoA = veiculo(a.organizacaoId(), "ABC1D23");
        veiculoB = veiculo(b.organizacaoId(), "ABC1D23");
    }

    @Test
    void placaNormalizadaEUnicaNoTenant() {
        assertViolation(() -> veiculo(a.organizacaoId(), " abc1d23 "),
                "23505", "uq_veiculo_frota_organizacao_placa");
    }

    @Test
    void placaHistoricaMantemUnicidadeSeparada() {
        veiculo(null, "HIS1A11");
        assertViolation(() -> veiculo(null, " his1a11 "),
                "23505", "uq_veiculo_frota_legado_placa");
    }

    @Test
    void recusaTransportadoraCrossTenant() {
        assertViolation(() -> vinculo(a.organizacaoId(), transportadoraB, veiculoA),
                "23503", "fk_tvf_transportadora_organizacao");
    }

    @Test
    void recusaVeiculoCrossTenant() {
        assertViolation(() -> vinculo(a.organizacaoId(), transportadoraA, veiculoB),
                "23503", "fk_tvf_veiculo_organizacao");
    }

    @Test
    void parEUnicoNoTenant() {
        vinculo(a.organizacaoId(), transportadoraA, veiculoA);
        assertViolation(() -> vinculo(a.organizacaoId(), transportadoraA, veiculoA),
                "23505", "uq_tvf_organizacao_par");
    }

    @Test
    void ownerDeVeiculoEImutavel() {
        assertViolation(() -> jdbc.update(
                        "UPDATE veiculo_frota SET organizacao_id=? WHERE id=?",
                        b.organizacaoId(), veiculoA),
                "23514", "ck_veiculo_frota_owner_imutavel");
    }

    @Test
    void ownerDeVeiculoHistoricoNaoPodeReceberTenant() {
        Long historico = veiculo(null, "HIS1A12");
        assertViolation(() -> jdbc.update(
                        "UPDATE veiculo_frota SET organizacao_id=? WHERE id=?",
                        a.organizacaoId(), historico),
                "23514", "ck_veiculo_frota_owner_imutavel");
    }

    @Test
    void ownerDeVeiculoTenantNaoPodeSerRemovido() {
        assertViolation(() -> jdbc.update(
                        "UPDATE veiculo_frota SET organizacao_id=NULL WHERE id=?", veiculoA),
                "23514", "ck_veiculo_frota_owner_imutavel");
    }

    @Test
    void ownerDoVinculoEImutavel() {
        Long id = vinculo(a.organizacaoId(), transportadoraA, veiculoA);
        assertViolation(() -> jdbc.update(
                        "UPDATE transportadora_veiculo_frota SET organizacao_id=? WHERE id=?",
                        b.organizacaoId(), id),
                "23514", "ck_tvf_owner_imutavel");
    }

    @Test
    void ownerDoVinculoHistoricoNaoPodeReceberTenant() {
        Long t = transportadora(null, "Historica", "44999999000191");
        Long v = veiculo(null, "HIS1A13");
        Long id = vinculo(null, t, v);
        assertViolation(() -> jdbc.update(
                        "UPDATE transportadora_veiculo_frota SET organizacao_id=? WHERE id=?",
                        a.organizacaoId(), id),
                "23514", "ck_tvf_owner_imutavel");
    }

    @Test
    void ownerDoVinculoTenantNaoPodeSerRemovido() {
        Long id = vinculo(a.organizacaoId(), transportadoraA, veiculoA);
        assertViolation(() -> jdbc.update(
                        "UPDATE transportadora_veiculo_frota SET organizacao_id=NULL WHERE id=?", id),
                "23514", "ck_tvf_owner_imutavel");
    }

    @Test
    void transportadoraHistoricaNaoPodeReceberTenantMesmoSemVinculo() {
        Long id = transportadora(null, "Sem vinculo", "44999999000192");
        assertViolation(() -> jdbc.update(
                        "UPDATE transportadora SET organizacao_id=? WHERE id=?",
                        a.organizacaoId(), id),
                "23514", "ck_transportadora_owner_imutavel");
    }

    @Test
    void transportadoraNaoPodeTrocarDeTenant() {
        assertViolation(() -> jdbc.update(
                        "UPDATE transportadora SET organizacao_id=? WHERE id=?",
                        b.organizacaoId(), transportadoraA),
                "23514", "ck_transportadora_owner_imutavel");
    }

    @Test
    void transportadoraTenantNaoPodePerderOwner() {
        assertViolation(() -> jdbc.update(
                        "UPDATE transportadora SET organizacao_id=NULL WHERE id=?", transportadoraA),
                "23514", "ck_transportadora_owner_imutavel");
    }

    @Test
    void bridgeHistoricoParaTransportadoraTenantERecusada() {
        Long veiculoHistorico = veiculo(null, "HIS1A14");
        assertViolation(() -> vinculo(null, transportadoraA, veiculoHistorico),
                "23503", "fk_tvf_transportadora_organizacao");
    }

    @Test
    void bridgeTenantParaVeiculoHistoricoERecusada() {
        Long veiculoHistorico = veiculo(null, "HIS1A15");
        assertViolation(() -> vinculo(a.organizacaoId(), transportadoraA, veiculoHistorico),
                "23503", "fk_tvf_veiculo_organizacao");
    }

    @Test
    void updateAdversarialDaTransportadoraERecusado() {
        Long id = vinculo(a.organizacaoId(), transportadoraA, veiculoA);
        assertViolation(() -> jdbc.update(
                        "UPDATE transportadora_veiculo_frota SET transportadora_id=? WHERE id=?",
                        transportadoraB, id),
                "23503", "fk_tvf_transportadora_organizacao");
    }

    @Test
    void updateAdversarialDoVeiculoERecusado() {
        Long id = vinculo(a.organizacaoId(), transportadoraA, veiculoA);
        assertViolation(() -> jdbc.update(
                        "UPDATE transportadora_veiculo_frota SET veiculo_frota_id=? WHERE id=?",
                        veiculoB, id),
                "23503", "fk_tvf_veiculo_organizacao");
    }

    @Test
    void backfillIsoladoDaTransportadoraHistoricaERecusado() {
        Long t = transportadora(null, "Historica", "44999999000199");
        Long v = veiculo(null, "HIS9A99");
        vinculo(null, t, v);
        assertViolation(() -> jdbc.update(
                        "UPDATE transportadora SET organizacao_id=? WHERE id=?",
                        a.organizacaoId(), t),
                "23514", "ck_transportadora_owner_imutavel");
    }

    @Test
    void guardEspecificoDeBackfillDaFrotaPermaneceInstalado() {
        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM pg_trigger
                WHERE tgrelid='transportadora'::regclass
                  AND tgname='tg_transportadora_backfill_frota'
                  AND NOT tgisinternal
                """, Integer.class);
        org.assertj.core.api.Assertions.assertThat(total).isEqualTo(1);
    }

    private Long transportadora(Long org, String nome, String documento) {
        return jdbc.queryForObject("""
                INSERT INTO transportadora
                    (organizacao_id,nome,tipo,documento,ativo,created_at,updated_at)
                VALUES (?,?,'JURIDICA',?,true,now(),now()) RETURNING id
                """, Long.class, org, nome, documento);
    }

    private Long veiculo(Long org, String placa) {
        return jdbc.queryForObject("""
                INSERT INTO veiculo_frota
                    (organizacao_id,placa,ativo,created_at,updated_at)
                VALUES (?,?,true,now(),now()) RETURNING id
                """, Long.class, org, placa);
    }

    private Long vinculo(Long org, Long transportadora, Long veiculo) {
        return jdbc.queryForObject("""
                INSERT INTO transportadora_veiculo_frota
                    (organizacao_id,transportadora_id,veiculo_frota_id,ativo,created_at,updated_at)
                VALUES (?,?,?,true,now(),now()) RETURNING id
                """, Long.class, org, transportadora, veiculo);
    }
}
