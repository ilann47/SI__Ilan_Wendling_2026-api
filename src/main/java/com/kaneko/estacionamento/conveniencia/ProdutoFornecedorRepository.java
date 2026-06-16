package com.kaneko.estacionamento.conveniencia;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio do vinculo {@link ProdutoFornecedor}.
 */
public interface ProdutoFornecedorRepository extends JpaRepository<ProdutoFornecedor, Long> {

    /** Unicidade do par (uq_produto_fornecedor). */
    boolean existsByProdutoIdAndFornecedorId(Long produtoId, Long fornecedorId);

    boolean existsByProdutoIdAndFornecedorIdAndIdNot(Long produtoId, Long fornecedorId, Long id);

    @Override
    @EntityGraph(attributePaths = {"produto", "fornecedor"})
    Page<ProdutoFornecedor> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"produto", "fornecedor"})
    Optional<ProdutoFornecedor> findById(Long id);
}
