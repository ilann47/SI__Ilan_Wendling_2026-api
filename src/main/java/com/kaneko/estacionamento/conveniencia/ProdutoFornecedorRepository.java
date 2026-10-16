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

    @EntityGraph(attributePaths = {"produto", "fornecedor"})
    Optional<ProdutoFornecedor> findByProdutoIdAndFornecedorIdAndOrganizacaoId(
            Long produtoId, Long fornecedorId, Long organizacaoId);

    @EntityGraph(attributePaths = {"produto", "fornecedor"})
    Optional<ProdutoFornecedor> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"produto", "fornecedor"})
    Optional<ProdutoFornecedor> findByIdAndOrganizacaoIdIsNull(Long id);

    @EntityGraph(attributePaths = {"produto", "fornecedor"})
    Page<ProdutoFornecedor> findAllByOrganizacaoId(Long organizacaoId, Pageable pageable);

    @EntityGraph(attributePaths = {"produto", "fornecedor"})
    Page<ProdutoFornecedor> findAllByOrganizacaoIdAndProdutoId(
            Long organizacaoId, Long produtoId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"produto", "fornecedor"})
    Page<ProdutoFornecedor> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"produto", "fornecedor"})
    Optional<ProdutoFornecedor> findById(Long id);
}
