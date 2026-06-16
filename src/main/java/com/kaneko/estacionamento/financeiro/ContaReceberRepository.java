package com.kaneko.estacionamento.financeiro;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link ContaReceber}.
 */
public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long> {

    @EntityGraph(attributePaths = {"cliente", "notaSaida", "formaPagamento"})
    List<ContaReceber> findByNotaSaidaId(Long notaSaidaId);

    @Override
    @EntityGraph(attributePaths = {"cliente", "notaSaida", "formaPagamento"})
    Page<ContaReceber> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cliente", "notaSaida", "formaPagamento"})
    Optional<ContaReceber> findById(Long id);
}
