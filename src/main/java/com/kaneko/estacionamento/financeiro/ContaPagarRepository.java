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
    List<ContaPagar> findByOrganizacaoIdAndNotaEntradaIdInOrderByDataVencimentoAsc(
            Long organizacaoId, List<Long> notaIds);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ContaPagar c where c.id = :id and "
            + "((:org is null and c.organizacaoId is null) or c.organizacaoId = :org)")
    Optional<ContaPagar> buscarParaBaixa(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("org") Long org);

    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    List<ContaPagar> findByNotaEntradaIdAndOrganizacaoIdOrderByNumeroParcelaAsc(
            Long notaEntradaId, Long organizacaoId);

    /** Titulos a pagar em aberto vencendo no intervalo — base do relatorio 4.28. */
    @EntityGraph(attributePaths = "fornecedor")
    List<ContaPagar> findByOrganizacaoIdIsNullAndSituacaoInAndDataVencimentoBetween(
            Collection<SituacaoContaPagar> situacoes, LocalDate inicio, LocalDate fim);
    @EntityGraph(attributePaths = "fornecedor")
    List<ContaPagar> findByOrganizacaoIdAndSituacaoInAndDataVencimentoBetween(
            Long organizacaoId, Collection<SituacaoContaPagar> situacoes,
            LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    Page<ContaPagar> findAllByOrganizacaoId(Long organizacaoId, Pageable pageable);

    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    Page<ContaPagar> findAllByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    Optional<ContaPagar> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"fornecedor", "notaEntrada", "formaPagamento"})
    Optional<ContaPagar> findByIdAndOrganizacaoIdIsNull(Long id);
}
