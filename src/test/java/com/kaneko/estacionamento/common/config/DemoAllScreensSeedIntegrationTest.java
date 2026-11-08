package com.kaneko.estacionamento.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.estoque.OrigemMovimentoService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TenantApiIntegrationTest
class DemoAllScreensSeedIntegrationTest {

    @Autowired private DemoDataSeedService baseSeed;
    @Autowired private DemoAllScreensSeedService allScreensSeed;
    @Autowired private OrigemMovimentoService origemMovimentoService;
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
        assertThat(primeiraExecucao.movimentosEstoque()).isGreaterThanOrEqualTo(2);
        assertThat(count("""
                select count(*) from movimento_estoque m
                join nota_entrada n on n.organizacao_id=m.organizacao_id and n.numero='NE-DEMO-001'
                where m.origem_tipo not in ('NOTA_ENTRADA', 'NOTA_SAIDA')
                """)).isZero();
        assertThat(primeiraExecucao.documentosFiscais()).isGreaterThanOrEqualTo(3);
        assertThat(primeiraExecucao.ordensServico()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.baixasFinanceiras()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.importacoesXml()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.movimentacoesPatio()).isGreaterThanOrEqualTo(2);
        assertThat(primeiraExecucao.pedidos()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.tentativasAcesso()).isGreaterThanOrEqualTo(2);

        var nota = jdbc.queryForMap("select numero,serie,modelo from nota_entrada where numero='NE-DEMO-001'");
        assertThat(nota.get("numero")).isEqualTo("NE-DEMO-001");
        assertThat(nota.get("serie")).isEqualTo("D1");
        assertThat(nota.get("modelo")).isEqualTo("55");
    }

    @Test
    void reaplicarSeedNaoReprecificaNotaConfirmadaExistente() {
        tenant.criarUsuario("admin", PerfilUsuario.ADMIN);
        baseSeed.seed("admin"); allScreensSeed.seed("admin", "operador-seed-test");
        jdbc.update("update nota_entrada set valor_frete_micros=0,valor_total_micros=40000000 where numero='NE-DEMO-001'");
        jdbc.update("update item_nota_entrada set rateio_frete_micros=0,custo_preco_final_micros=2000000 where nota_entrada_id in "
                +"(select id from nota_entrada where numero='NE-DEMO-001')");
        allScreensSeed.seed("admin", "operador-seed-test");
        assertThat(jdbc.queryForObject("select valor_total_micros * 0.000001 from nota_entrada where numero='NE-DEMO-001'",BigDecimal.class))
                .isEqualByComparingTo("40");
        assertThat(jdbc.queryForObject("select custo_preco_final_micros * 0.000001 from item_nota_entrada where nota_entrada_id in "
                +"(select id from nota_entrada where numero='NE-DEMO-001')",BigDecimal.class)).isEqualByComparingTo("2");
    }

    @Test
    void notaDiretaEstoqueEContaPagarMantemComposicaoSemOC() {
        tenant.criarUsuario("admin", PerfilUsuario.ADMIN);
        baseSeed.seed("admin"); allScreensSeed.seed("admin", "operador-seed-test");
        var valores=jdbc.queryForMap("""
            select n.valor_total_micros * 0.000001 total_nota,i.rateio_frete_micros * 0.000001 frete_item_nota,i.custo_preco_final_micros * 0.000001 custo_unitario_nota,
                   cp.valor_total_micros * 0.000001 total_conta,me.custo_unitario_micros * 0.000001 custo_unitario_estoque
            from nota_entrada n join item_nota_entrada i on i.nota_entrada_id=n.id
            join conta_pagar cp on cp.nota_entrada_id=n.id
            join movimento_estoque me on me.organizacao_id=n.organizacao_id and me.produto_id=i.produto_id
              and me.origem_tipo='NOTA_ENTRADA' and me.origem_chave=concat(n.id,':1')
            where n.numero='NE-DEMO-001'
            """);
        assertThat((BigDecimal) valores.get("total_nota")).isEqualByComparingTo("45.00");
        assertThat((BigDecimal) valores.get("frete_item_nota")).isEqualByComparingTo("5.00");
        assertThat((BigDecimal) valores.get("custo_unitario_nota")).isEqualByComparingTo("2.25");
        assertThat((BigDecimal) valores.get("total_conta")).isEqualByComparingTo("45.00");
        assertThat((BigDecimal) valores.get("custo_unitario_estoque")).isEqualByComparingTo("2.25");
    }

    @Test
    void preservaNotaDeSaidaDemonstrativaEContaSemPedido() {
        tenant.criarUsuario("admin", PerfilUsuario.ADMIN);
        baseSeed.seed("admin");
        allScreensSeed.seed("admin", "operador-seed-test");
        Long organizacaoId = jdbc.queryForObject(
                "select id from organizacao where documento = ?", Long.class,
                DemoDataSeedService.DOCUMENTO_ORGANIZACAO);
        assertThat(jdbc.queryForObject("select to_regclass('public.venda_administrativa')::text",
                String.class)).isNull();
        assertThat(jdbc.queryForObject("select to_regclass('public.item_venda_administrativa')::text",
                String.class)).isNull();
        assertThat(count("""
                select count(*) from information_schema.columns where table_schema='public'
                  and table_name in ('nota_saida','conta_receber') and column_name='venda_administrativa_id'
                """)).isZero();
        assertThat(count("""
                select count(*) from nota_saida n
                join conta_receber cr on cr.nota_saida_id=n.id and cr.organizacao_id=n.organizacao_id
                where n.organizacao_id=? and n.numero='NS-DEMO-001' and n.situacao='CONFIRMADA'
                """, organizacaoId)).isEqualTo(1);
        assertThat(count("""
                select count(*) from movimento_estoque where organizacao_id=?
                  and origem_tipo='NOTA_SAIDA' and origem_chave='NS-DEMO-001'
                """, organizacaoId)).isEqualTo(1);
    }

    @Test
    void navegaDaRazaoDeEstoqueParaNotaLegadaIdentificadaPeloNumero() {
        tenant.criarUsuario("admin", PerfilUsuario.ADMIN);
        baseSeed.seed("admin");
        allScreensSeed.seed("admin", "operador-seed-test");
        Long organizacaoId = jdbc.queryForObject(
                "select id from organizacao where documento = ?", Long.class,
                DemoDataSeedService.DOCUMENTO_ORGANIZACAO);
        Long membershipId = jdbc.queryForObject("""
                select m.id from membro_organizacao m
                join usuario u on u.id = m.usuario_id
                where m.organizacao_id = ? and u.login = 'admin'
                """, Long.class, organizacaoId);
        Long movimentoId = jdbc.queryForObject("""
                select id from movimento_estoque
                where organizacao_id = ? and origem_tipo = 'NOTA_SAIDA'
                  and origem_chave = 'NS-DEMO-001'
                """, Long.class, organizacaoId);

        var origem = origemMovimentoService.buscar(organizacaoId, membershipId, movimentoId);

        assertThat(origem.descricao()).isEqualTo("Nota de saída NS-DEMO-001");
        assertThat(origem.caminho()).matches("/app/notas-saida\\?detail=[1-9][0-9]*");
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
                count("select count(*) from ordem_servico where organizacao_id = ?", organizacaoId),
                count("select count(*) from baixa_financeira where organizacao_id = ?", organizacaoId),
                count("select count(*) from importacao_nota_entrada where organizacao_id = ?", organizacaoId),
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
            long ordensServico,
            long baixasFinanceiras,
            long importacoesXml,
            long movimentacoesPatio,
            long pedidos,
            long tentativasAcesso) {
    }
}
