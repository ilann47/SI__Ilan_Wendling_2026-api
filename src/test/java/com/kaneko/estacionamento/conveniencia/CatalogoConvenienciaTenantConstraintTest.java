package com.kaneko.estacionamento.conveniencia;

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
class CatalogoConvenienciaTenantConstraintTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantTestSupport tenant;
    @Autowired private CategoriaRepository categorias;
    @Autowired private MarcaRepository marcas;
    @Autowired private UnidadeMedidaRepository unidades;

    private TenantFixture tenantA;
    private TenantFixture tenantB;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenant.criarTenantAdmin();
        tenantB = tenant.criarTenantAdmin();
    }

    @Test
    void bancoNormalizaUnicidadesSomenteDentroDoTenant() {
        inserirCategoria(tenantA.organizacaoId(), "Bebidas");
        inserirCategoria(tenantB.organizacaoId(), " bebidas ");
        assertViolation(() -> inserirCategoria(tenantA.organizacaoId(), " BEBIDAS "),
                "23505", "uq_categoria_organizacao_nome");
    }

    @Test
    void bancoNormalizaNomeDaMarcaDentroDoTenant() {
        inserirMarca(tenantA.organizacaoId(), "Kaneko");
        inserirMarca(tenantB.organizacaoId(), " kaneko ");
        assertViolation(() -> inserirMarca(tenantA.organizacaoId(), " KANEKO "),
                "23505", "uq_marca_organizacao_nome");
    }

    @Test
    void bancoNormalizaSiglaDaUnidadeDentroDoTenant() {
        inserirUnidade(tenantA.organizacaoId(), "Unidade", "UN");
        inserirUnidade(tenantB.organizacaoId(), "Unidade", " un ");
        assertViolation(() -> inserirUnidade(tenantA.organizacaoId(), "Unidades", " un "),
                "23505", "uq_unidade_medida_organizacao_sigla");
    }

    @Test
    void bancoNormalizaNomeDaUnidadeNoTenantELegado() {
        inserirUnidade(tenantA.organizacaoId(), "Caixa", "CX");
        assertViolation(() -> inserirUnidade(tenantA.organizacaoId(), " caixa ", "C2"),
                "23505", "uq_unidade_medida_organizacao_nome");
    }

    @Test
    void bancoNormalizaNomeDaUnidadeLegada() {
        inserirUnidade(null, "Caixa legada", "CL1");
        assertViolation(() -> inserirUnidade(null, " caixa LEGADA ", "CL2"),
                "23505", "uq_unidade_medida_legado_nome");
    }

    @Test
    void bancoPreservaHistoricoEFindersLegados() {
        Long categoriaTenant = inserirCategoria(tenantA.organizacaoId(), "Tenant");
        Long categoriaLegada = inserirCategoria(null, "Legada");
        Long marcaTenant = inserirMarca(tenantA.organizacaoId(), "Tenant");
        Long marcaLegada = inserirMarca(null, "Legada");
        Long unidadeTenant = inserirUnidade(tenantA.organizacaoId(), "Tenant", "TEN");
        Long unidadeLegada = inserirUnidade(null, "Legada", "LEG");

        assertThat(categorias.findByIdAndOrganizacaoIdIsNull(categoriaTenant)).isEmpty();
        assertThat(categorias.findByIdAndOrganizacaoIdIsNull(categoriaLegada)).isPresent();
        assertThat(marcas.findByIdAndOrganizacaoIdIsNull(marcaTenant)).isEmpty();
        assertThat(marcas.findByIdAndOrganizacaoIdIsNull(marcaLegada)).isPresent();
        assertThat(unidades.findByIdAndOrganizacaoIdIsNull(unidadeTenant)).isEmpty();
        assertThat(unidades.findByIdAndOrganizacaoIdIsNull(unidadeLegada)).isPresent();
    }

    @Test
    void produtoGlobalRecusaMarcaTenant() {
        Long marca = inserirMarca(tenantA.organizacaoId(), "Tenant");
        Long unidade = inserirUnidade(null, "Historica", "HIS");
        assertViolation(() -> inserirProduto(marca, unidade, null),
                "23503", "fk_produto_marca_owner");
    }

    @Test
    void produtoGlobalRecusaUnidadeTenant() {
        Long marca = inserirMarca(null, "Historica");
        Long unidade = inserirUnidade(tenantA.organizacaoId(), "Tenant", "TEN");
        assertViolation(() -> inserirProduto(marca, unidade, null),
                "23503", "fk_produto_unidade_owner");
    }

    @Test
    void produtoGlobalRecusaCategoriaTenant() {
        Long marca = inserirMarca(null, "Historica");
        Long unidade = inserirUnidade(null, "Historica", "HIS");
        Long categoria = inserirCategoria(tenantA.organizacaoId(), "Tenant");
        assertViolation(() -> inserirProduto(marca, unidade, categoria),
                "23503", "fk_produto_categoria_owner");
    }

    @Test
    void ownersDosTresCadastrosSaoImutaveis() {
        Long categoria = inserirCategoria(null, "Categoria historica");
        assertViolation(() -> jdbc.update(
                        "UPDATE categoria SET organizacao_id=? WHERE id=?",
                        tenantA.organizacaoId(), categoria),
                "23514", "ck_categoria_owner_imutavel");
    }

    @Test
    void ownerDaMarcaEImutavel() {
        Long marca = inserirMarca(null, "Marca historica");
        assertViolation(() -> jdbc.update(
                        "UPDATE marca SET organizacao_id=? WHERE id=?",
                        tenantA.organizacaoId(), marca),
                "23514", "ck_marca_owner_imutavel");
    }

    @Test
    void ownerDaUnidadeEImutavel() {
        Long unidade = inserirUnidade(null, "Unidade historica", "HIS");
        assertViolation(() -> jdbc.update(
                        "UPDATE unidade_medida SET organizacao_id=? WHERE id=?",
                        tenantA.organizacaoId(), unidade),
                "23514", "ck_unidade_medida_owner_imutavel");
    }

    @Test
    void ownerTenantNaoPodeTrocarDeOrganizacao() {
        Long categoria = inserirCategoria(tenantA.organizacaoId(), "Categoria A");
        assertViolation(() -> jdbc.update("UPDATE categoria SET organizacao_id=? WHERE id=?",
                        tenantB.organizacaoId(), categoria),
                "23514", "ck_categoria_owner_imutavel");
    }

    @Test
    void ownerTenantNaoPodeSerRemovido() {
        Long categoria = inserirCategoria(tenantA.organizacaoId(), "Categoria A");
        assertViolation(() -> jdbc.update(
                        "UPDATE categoria SET organizacao_id=NULL WHERE id=?", categoria),
                "23514", "ck_categoria_owner_imutavel");
    }

    @Test
    void bancoRecusaOrganizacaoInexistente() {
        assertViolation(() -> inserirCategoria(Long.MAX_VALUE, "Invalida"),
                "23503", "fk_categoria_organizacao");
    }

    @Test
    void bancoRecusaOrganizacaoInexistenteNaMarca() {
        assertViolation(() -> inserirMarca(Long.MAX_VALUE, "Invalida"),
                "23503", "fk_marca_organizacao");
    }

    @Test
    void bancoRecusaOrganizacaoInexistenteNaUnidade() {
        assertViolation(() -> inserirUnidade(Long.MAX_VALUE, "Invalida", "INV"),
                "23503", "fk_unidade_medida_organizacao");
    }

    @Test
    void reverseGuardRecusaBackfillDeMarcaReferenciada() {
        Long marca = inserirMarca(null, "Marca historica");
        Long unidade = inserirUnidade(null, "Unidade historica", "UH1");
        inserirProduto(marca, unidade, null);
        assertViolation(() -> jdbc.update("UPDATE marca SET organizacao_id=? WHERE id=?",
                        tenantA.organizacaoId(), marca),
                "23503", "fk_produto_marca_owner");
    }

    @Test
    void reverseGuardRecusaBackfillDeUnidadeReferenciada() {
        Long marca = inserirMarca(null, "Marca historica");
        Long unidade = inserirUnidade(null, "Unidade historica", "UH2");
        inserirProduto(marca, unidade, null);
        assertViolation(() -> jdbc.update("UPDATE unidade_medida SET organizacao_id=? WHERE id=?",
                        tenantA.organizacaoId(), unidade),
                "23503", "fk_produto_unidade_owner");
    }

    @Test
    void reverseGuardRecusaBackfillDeCategoriaReferenciada() {
        Long marca = inserirMarca(null, "Marca historica");
        Long unidade = inserirUnidade(null, "Unidade historica", "UH3");
        Long categoria = inserirCategoria(null, "Categoria historica");
        inserirProduto(marca, unidade, categoria);
        assertViolation(() -> jdbc.update("UPDATE categoria SET organizacao_id=? WHERE id=?",
                        tenantA.organizacaoId(), categoria),
                "23503", "fk_produto_categoria_owner");
    }

    @Test
    void updateAdversarialDeProdutoRecusaMarcaTenant() {
        Long marca = inserirMarca(null, "Marca historica");
        Long unidade = inserirUnidade(null, "Unidade historica", "UH4");
        Long produto = inserirProduto(marca, unidade, null);
        Long marcaTenant = inserirMarca(tenantA.organizacaoId(), "Marca tenant");
        assertViolation(() -> jdbc.update("UPDATE produto SET marca_id=? WHERE id=?",
                        marcaTenant, produto),
                "23503", "fk_produto_marca_owner");
    }

    @Test
    void updateAdversarialDeProdutoRecusaUnidadeTenant() {
        Long marca = inserirMarca(null, "Marca historica");
        Long unidade = inserirUnidade(null, "Unidade historica", "UH5");
        Long produto = inserirProduto(marca, unidade, null);
        Long unidadeTenant = inserirUnidade(tenantA.organizacaoId(), "Unidade tenant", "UT1");
        assertViolation(() -> jdbc.update("UPDATE produto SET unidade_medida_id=? WHERE id=?",
                        unidadeTenant, produto),
                "23503", "fk_produto_unidade_owner");
    }

    @Test
    void updateAdversarialDeProdutoRecusaCategoriaTenant() {
        Long marca = inserirMarca(null, "Marca historica");
        Long unidade = inserirUnidade(null, "Unidade historica", "UH6");
        Long produto = inserirProduto(marca, unidade, null);
        Long categoriaTenant = inserirCategoria(tenantA.organizacaoId(), "Categoria tenant");
        assertViolation(() -> jdbc.update("UPDATE produto SET categoria_id=? WHERE id=?",
                        categoriaTenant, produto),
                "23503", "fk_produto_categoria_owner");
    }

    private Long inserirCategoria(Long organizacaoId, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO categoria (organizacao_id,nome,ativo,created_at,updated_at)
                VALUES (?,?,true,now(),now()) RETURNING id
                """, Long.class, organizacaoId, nome);
    }

    private Long inserirMarca(Long organizacaoId, String nome) {
        return jdbc.queryForObject("""
                INSERT INTO marca (organizacao_id,nome,ativo,created_at,updated_at)
                VALUES (?,?,true,now(),now()) RETURNING id
                """, Long.class, organizacaoId, nome);
    }

    private Long inserirUnidade(Long organizacaoId, String nome, String sigla) {
        return jdbc.queryForObject("""
                INSERT INTO unidade_medida
                    (organizacao_id,nome,sigla,ativo,created_at,updated_at)
                VALUES (?,?,?,true,now(),now()) RETURNING id
                """, Long.class, organizacaoId, nome, sigla);
    }

    private Long inserirProduto(Long marcaId, Long unidadeId, Long categoriaId) {
        return jdbc.queryForObject("""
                INSERT INTO produto
                    (nome,marca_id,unidade_medida_id,categoria_id,ativo,created_at,updated_at)
                VALUES ('Produto global',?,?,?,true,now(),now()) RETURNING id
                """, Long.class, marcaId, unidadeId, categoriaId);
    }
}
