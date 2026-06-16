package com.kaneko.estacionamento.fiscal;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link NotaEntrada}. As consultas carregam os itens (e seus
 * produtos) e o fornecedor via {@link EntityGraph}.
 */
public interface NotaEntradaRepository extends JpaRepository<NotaEntrada, Long> {

    boolean existsByNumeroAndSerieAndFornecedorId(String numero, String serie, Long fornecedorId);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "itens", "itens.produto"})
    Page<NotaEntrada> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "condicaoPagamento", "itens", "itens.produto"})
    Optional<NotaEntrada> findById(Long id);
}
