package com.kaneko.estacionamento.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TenantApiIntegrationTest
class DemoAllScreensSeedIntegrationTest {

    @Autowired private DemoDataSeedService baseSeed;
    @Autowired private DemoAllScreensSeedService allScreensSeed;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;

    @Test
    void populaPerfisETelasSemDuplicarDados() {
        tenant.criarUsuario("admin", PerfilUsuario.ADMIN);
        baseSeed.seed("admin");
        allScreensSeed.seed("admin", "operador-seed-test");
        SeedCounts primeiraExecucao = counts();

        allScreensSeed.seed("admin", "operador-seed-test");

        assertThat(counts()).isEqualTo(primeiraExecucao);
        assertThat(primeiraExecucao.usuarios()).isEqualTo(2);
        assertThat(primeiraExecucao.memberships()).isEqualTo(2);
        assertThat(primeiraExecucao.produtos()).isGreaterThanOrEqualTo(2);
        assertThat(primeiraExecucao.movimentosEstoque()).isGreaterThanOrEqualTo(3);
        assertThat(primeiraExecucao.documentosFiscais()).isGreaterThanOrEqualTo(3);
        assertThat(primeiraExecucao.ordensCompra()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.vendasAdministrativas()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.movimentacoesPatio()).isGreaterThanOrEqualTo(2);
        assertThat(primeiraExecucao.pedidos()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.tentativasAcesso()).isGreaterThanOrEqualTo(2);
    }

    private SeedCounts counts() {
        Long organizacaoId = jdbc.queryForObject(
                "select id from organizacao where documento = ?",
                Long.class,
                DemoDataSeedService.DOCUMENTO_ORGANIZACAO);
        return new SeedCounts(
                count("select count(*) from usuario where login in ('admin', 'operador-seed-test')"),
                count("select count(*) from membro_organizacao where organizacao_id = ?", organizacaoId),
                count("select count(*) from produto where organizacao_id = ?", organizacaoId),
                count("select count(*) from movimento_estoque where organizacao_id = ?", organizacaoId),
                count("select (select count(*) from nota_entrada where organizacao_id = ?)"
                                + " + (select count(*) from nota_saida where organizacao_id = ?)"
                                + " + (select count(*) from nota_servico where organizacao_id = ?)",
                        organizacaoId, organizacaoId, organizacaoId),
                count("select count(*) from ordem_compra where organizacao_id = ?", organizacaoId),
                count("select count(*) from venda_administrativa where organizacao_id = ?", organizacaoId),
                count("select count(*) from movimentacao where organizacao_id = ?", organizacaoId),
                count("select count(*) from pedido where organizacao_id = ?", organizacaoId),
                count("select count(*) from tentativa_acesso where organizacao_id = ?", organizacaoId));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private record SeedCounts(
            long usuarios,
            long memberships,
            long produtos,
            long movimentosEstoque,
            long documentosFiscais,
            long ordensCompra,
            long vendasAdministrativas,
            long movimentacoesPatio,
            long pedidos,
            long tentativasAcesso) {
    }
}
