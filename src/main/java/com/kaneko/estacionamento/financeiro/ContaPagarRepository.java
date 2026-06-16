package com.kaneko.estacionamento.financeiro;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link ContaPagar}.
 */
public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long> {

    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    List<ContaPagar> findByNotaEntradaId(Long notaEntradaId);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    Page<ContaPagar> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    Optional<ContaPagar> findById(Long id);
}
