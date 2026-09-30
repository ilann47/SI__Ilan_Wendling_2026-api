package com.kaneko.estacionamento.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.fiscal.VendaDocumentoService;
import com.kaneko.estacionamento.estoque.OrigemMovimentoService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TenantApiIntegrationTest
class DemoAllScreensSeedIntegrationTest {

    @Autowired private DemoDataSeedService baseSeed;
    @Autowired private DemoAllScreensSeedService allScreensSeed;
    @Autowired private VendaDocumentoService vendaDocumentoService;
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
        assertThat(primeiraExecucao.movimentosEstoque()).isGreaterThanOrEqualTo(3);
        assertThat(primeiraExecucao.documentosFiscais()).isGreaterThanOrEqualTo(3);
        assertThat(primeiraExecucao.ordensCompra()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.recebimentosCompra()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.notasEntradaVinculadas()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.vendasAdministrativas()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.vendasConfirmadas()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.notasSaidaVinculadas()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.ordensServico()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.baixasFinanceiras()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.importacoesXml()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.movimentacoesPatio()).isGreaterThanOrEqualTo(2);
        assertThat(primeiraExecucao.pedidos()).isGreaterThanOrEqualTo(1);
        assertThat(primeiraExecucao.tentativasAcesso()).isGreaterThanOrEqualTo(2);

        var chaveFiscal = jdbc.queryForMap("""
                select numero_nota, serie_nota, modelo_nota
                  from ordem_compra
                 where numero = 'OC-DEMO-001'
                """);
        assertThat(chaveFiscal.get("numero_nota")).isEqualTo("NE-DEMO-001");
        assertThat(chaveFiscal.get("serie_nota")).isEqualTo("D1");
        assertThat(chaveFiscal.get("modelo_nota")).isEqualTo("55");
    }

    @Test
    void reaplicaCustosDaCompraDemonstrativaEmBaseLocalPreexistente() {
        tenant.criarUsuario("admin", PerfilUsuario.ADMIN);
        baseSeed.seed("admin");
        allScreensSeed.seed("admin", "operador-seed-test");
        jdbc.update("""
                update item_ordem_compra i
                   set rateio_frete = 0, custo_total = 40, custo_unitario_final = 2
                  from ordem_compra o
                 where o.id = i.ordem_compra_id and o.numero = 'OC-DEMO-001'
                """);

        allScreensSeed.seed("admin", "operador-seed-test");

        var custos = jdbc.queryForMap("""
                select i.rateio_frete, i.custo_total, i.custo_unitario_final
                  from item_ordem_compra i
                  join ordem_compra o on o.id = i.ordem_compra_id
                 where o.numero = 'OC-DEMO-001'
                """);
        assertThat(custos.get("rateio_frete")).isEqualTo(new BigDecimal("5.00"));
        assertThat(custos.get("custo_total")).isEqualTo(new BigDecimal("45.00"));
        assertThat(custos.get("custo_unitario_final")).isEqualTo(new BigDecimal("2.25"));
    }

    @Test
    void mantemOrdemNotaEstoqueEContaPagarComOMesmoCustoTotal() {
        tenant.criarUsuario("admin", PerfilUsuario.ADMIN);
        baseSeed.seed("admin");
        allScreensSeed.seed("admin", "operador-seed-test");

        var valores = jdbc.queryForMap("""
                select o.valor_total as total_ordem,
                       n.valor_total as total_nota,
                       i.rateio_frete as frete_item_nota,
                       i.custo_preco_final as custo_unitario_nota,
                       cp.valor_total as total_conta,
                       me.custo_unitario as custo_unitario_estoque
                  from ordem_compra o
                  join recebimento_compra r on r.ordem_compra_id = o.id
                  join nota_entrada n on n.recebimento_compra_id = r.id
                  join item_nota_entrada i on i.nota_entrada_id = n.id and i.sequencia = 1
                  join conta_pagar cp on cp.nota_entrada_id = n.id and cp.numero_parcela = 1
                  join movimento_estoque me on me.organizacao_id = o.organizacao_id
                     and me.origem_tipo = 'NOTA_ENTRADA'
                     and me.origem_chave = n.numero
                     and me.produto_id = i.produto_id
                 where o.numero = 'OC-DEMO-001'
                """);

        assertThat(valores.get("total_ordem")).isEqualTo(new BigDecimal("45.00"));
        assertThat(valores.get("total_nota")).isEqualTo(new BigDecimal("45.00"));
        assertThat(valores.get("frete_item_nota")).isEqualTo(new BigDecimal("5.00"));
        assertThat(valores.get("custo_unitario_nota")).isEqualTo(new BigDecimal("2.25"));
        assertThat(valores.get("total_conta")).isEqualTo(new BigDecimal("45.00"));
        assertThat(valores.get("custo_unitario_estoque")).isEqualTo(new BigDecimal("2.25"));
    }

    @Test
    void exibeMovimentoLegadoDaNotaNoEncadeamentoDaVendaDemonstrativa() {
        tenant.criarUsuario("admin", PerfilUsuario.ADMIN);
        baseSeed.seed("admin");
        allScreensSeed.seed("admin", "operador-seed-test");
        Long organizacaoId = jdbc.queryForObject(
                "select id from organizacao where documento = ?", Long.class,
                DemoDataSeedService.DOCUMENTO_ORGANIZACAO);
        Long vendaId = jdbc.queryForObject(
                "select id from venda_administrativa where organizacao_id = ? and numero = 'VENDA-DEMO-002'",
                Long.class, organizacaoId);

        var documentos = vendaDocumentoService.documentos(
                organizacaoId, vendaId, true, true, true);

        assertThat(documentos.movimentos()).hasSize(1);
        assertThat(documentos.movimentos().getFirst().origemTipo()).isEqualTo("NOTA_SAIDA");
        assertThat(documentos.movimentos().getFirst().origemChave()).isEqualTo("NS-DEMO-001");
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
                count("select count(*) from ordem_compra where organizacao_id = ?", organizacaoId),
                count("select count(*) from recebimento_compra where organizacao_id = ?", organizacaoId),
                count("select count(*) from nota_entrada where organizacao_id = ?"
                        + " and recebimento_compra_id is not null", organizacaoId),
                count("select count(*) from venda_administrativa where organizacao_id = ?", organizacaoId),
                count("select count(*) from venda_administrativa where organizacao_id = ?"
                        + " and status = 'CONFIRMADA'", organizacaoId),
                count("select count(*) from nota_saida where organizacao_id = ?"
                        + " and venda_administrativa_id is not null", organizacaoId),
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
            long ordensCompra,
            long recebimentosCompra,
            long notasEntradaVinculadas,
            long vendasAdministrativas,
            long vendasConfirmadas,
            long notasSaidaVinculadas,
            long ordensServico,
            long baixasFinanceiras,
            long importacoesXml,
            long movimentacoesPatio,
            long pedidos,
            long tentativasAcesso) {
    }
}
