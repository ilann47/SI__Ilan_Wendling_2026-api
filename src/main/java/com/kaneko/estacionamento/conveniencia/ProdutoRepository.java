package com.kaneko.estacionamento.conveniencia;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Repositorio de {@link Produto}. As consultas carregam marca, unidade de
 * medida e categoria via {@link EntityGraph}; os filtros dinamicos das
 * listagens usam {@link JpaSpecificationExecutor}.
 */
public interface ProdutoRepository extends JpaRepository<Produto, Long>,
        JpaSpecificationExecutor<Produto> {

    /** Unicidade do codigo de barras (uq_produto_codigo_barras), quando informado. */
    @Query("""
            select count(p)>0 from Produto p where p.organizacaoId=:org
            and trim(p.codigoBarras)=trim(:codigo) and (:id is null or p.id<>:id)
            """)
    boolean existeCodigoNormalizado(Long org, String codigo, Long id);

    @EntityGraph(attributePaths = {"marca", "unidadeMedida", "categoria"})
    Optional<Produto> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"marca", "unidadeMedida", "categoria"})
    Optional<Produto> findByIdAndOrganizacaoIdIsNull(Long id);

    /** Produtos ativos com estoque igual ou abaixo do minimo (relatorio 4.29). */
    @EntityGraph(attributePaths = {"marca", "categoria"})
    @Query("SELECT p FROM Produto p WHERE p.organizacaoId is null AND p.ativo = true AND p.quantidade <= p.quantidadeMinima ORDER BY p.nome")
    List<Produto> findComEstoqueAbaixoDoMinimo();

    @Override
    @EntityGraph(attributePaths = {"marca", "unidadeMedida", "categoria"})
    Optional<Produto> findById(Long id);
}
