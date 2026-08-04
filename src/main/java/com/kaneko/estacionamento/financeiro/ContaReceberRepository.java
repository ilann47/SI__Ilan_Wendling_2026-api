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
 * Repositorio de {@link ContaReceber}.
 */
public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long> {

    @EntityGraph(attributePaths = {"cliente", "notaSaida", "formaPagamento"})
    List<ContaReceber> findByNotaSaidaIdAndOrganizacaoId(Long notaSaidaId, Long organizacaoId);

    /** Recebimentos efetivados no intervalo — regime de caixa (relatorio 4.27). */
    @EntityGraph(attributePaths = "cliente")
    List<ContaReceber> findByOrganizacaoIdIsNullAndDataRecebimentoBetween(LocalDate inicio, LocalDate fim);

    /** Titulos a receber em aberto vencendo no intervalo — base do relatorio 4.28. */
    @EntityGraph(attributePaths = "cliente")
    List<ContaReceber> findByOrganizacaoIdIsNullAndSituacaoInAndDataVencimentoBetween(
            Collection<SituacaoContaReceber> situacoes, LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = {"cliente", "notaSaida", "formaPagamento"})
    Page<ContaReceber> findAllByOrganizacaoId(Long organizacaoId, Pageable pageable);
    @EntityGraph(attributePaths = {"cliente", "notaSaida", "formaPagamento"})
    Page<ContaReceber> findAllByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "notaSaida", "formaPagamento"})
    Optional<ContaReceber> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    @EntityGraph(attributePaths = {"cliente", "notaSaida", "formaPagamento"})
    Optional<ContaReceber> findByIdAndOrganizacaoIdIsNull(Long id);
}
