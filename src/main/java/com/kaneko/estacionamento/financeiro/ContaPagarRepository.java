package com.kaneko.estacionamento.financeiro;

import java.time.LocalDate;
import java.util.Collection;
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

    /** Titulos a pagar em aberto vencendo no intervalo — base do relatorio 4.28. */
    @EntityGraph(attributePaths = "fornecedor")
    List<ContaPagar> findBySituacaoInAndDataVencimentoBetween(
            Collection<SituacaoContaPagar> situacoes, LocalDate inicio, LocalDate fim);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    Page<ContaPagar> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    Optional<ContaPagar> findById(Long id);
}
