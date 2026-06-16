package com.kaneko.estacionamento.fiscal;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link NotaServico}.
 */
public interface NotaServicoRepository extends JpaRepository<NotaServico, Long> {

    boolean existsByNumeroAndSerie(String numero, String serie);

    @Override
    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento"})
    Page<NotaServico> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento"})
    Optional<NotaServico> findById(Long id);
}
