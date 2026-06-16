package com.kaneko.estacionamento.conveniencia;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repositorio de {@link Produto}. As consultas carregam marca, unidade de
 * medida e categoria via {@link EntityGraph}.
 */
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /** Unicidade do codigo de barras (uq_produto_codigo_barras), quando informado. */
    boolean existsByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarrasAndIdNot(String codigoBarras, Long id);

    /** Produtos ativos com estoque igual ou abaixo do minimo (relatorio 4.29). */
    @EntityGraph(attributePaths = {"marca", "categoria"})
    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND p.quantidade <= p.quantidadeMinima ORDER BY p.nome")
    List<Produto> findComEstoqueAbaixoDoMinimo();

    @Override
    @EntityGraph(attributePaths = {"marca", "unidadeMedida", "categoria"})
    Page<Produto> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"marca", "unidadeMedida", "categoria"})
    Optional<Produto> findById(Long id);
}
