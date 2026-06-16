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
 * Repositorio de {@link ContaPagarAvulsa}.
 */
public interface ContaPagarAvulsaRepository extends JpaRepository<ContaPagarAvulsa, Long> {

    /** Despesas avulsas em aberto vencendo no intervalo — base do relatorio 4.28. */
    @EntityGraph(attributePaths = "fornecedor")
    List<ContaPagarAvulsa> findBySituacaoInAndDataVencimentoBetween(
            Collection<SituacaoContaPagar> situacoes, LocalDate inicio, LocalDate fim);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "formaPagamento"})
    Page<ContaPagarAvulsa> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"fornecedor", "formaPagamento"})
    Optional<ContaPagarAvulsa> findById(Long id);
}
