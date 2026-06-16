package com.kaneko.estacionamento.financeiro;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link ContaPagarAvulsa}.
 */
public interface ContaPagarAvulsaRepository extends JpaRepository<ContaPagarAvulsa, Long> {

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "formaPagamento"})
    Page<ContaPagarAvulsa> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "formaPagamento"})
    Optional<ContaPagarAvulsa> findById(Long id);
}
