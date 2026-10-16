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

    @org.springframework.data.jpa.repository.Query("select c.id from ContaReceber c where c.notaServico.id = :notaId "
            + "and ((:org is null and c.organizacaoId is null) or c.organizacaoId = :org) order by c.id")
    List<Long> findIdsByNotaServicoIdAndOrganizacaoId(
            @org.springframework.data.repository.query.Param("notaId") Long notaId,
            @org.springframework.data.repository.query.Param("org") Long org);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ContaReceber c where c.id = :id and "
            + "((:org is null and c.organizacaoId is null) or c.organizacaoId = :org)")
    Optional<ContaReceber> buscarParaBaixa(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("org") Long org);

    @EntityGraph(attributePaths = {"cliente", "notaSaida", "notaServico", "formaPagamento", "vendaAdministrativa"})
    List<ContaReceber> findByVendaAdministrativaIdAndOrganizacaoIdOrderByNumeroParcelaAsc(
            Long vendaAdministrativaId, Long organizacaoId);

    @EntityGraph(attributePaths = {"cliente", "notaSaida", "notaServico", "formaPagamento"})
    List<ContaReceber> findByNotaSaidaIdAndOrganizacaoId(Long notaSaidaId, Long organizacaoId);
    @EntityGraph(attributePaths = {"cliente", "notaServico", "formaPagamento"})
    List<ContaReceber> findByNotaServicoIdAndOrganizacaoId(Long notaServicoId, Long organizacaoId);
    @EntityGraph(attributePaths = {"cliente", "notaServico", "formaPagamento"})
    List<ContaReceber> findByNotaServicoIdAndOrganizacaoIdIsNull(Long notaServicoId);

    /** Recebimentos efetivados no intervalo — regime de caixa (relatorio 4.27). */
    @EntityGraph(attributePaths = "cliente")
    List<ContaReceber> findByOrganizacaoIdIsNullAndDataRecebimentoBetween(LocalDate inicio, LocalDate fim);
    @EntityGraph(attributePaths = "cliente")
    List<ContaReceber> findByOrganizacaoIdAndDataRecebimentoBetween(
            Long organizacaoId, LocalDate inicio, LocalDate fim);

    /** Titulos a receber em aberto vencendo no intervalo — base do relatorio 4.28. */
    @EntityGraph(attributePaths = "cliente")
    List<ContaReceber> findByOrganizacaoIdIsNullAndSituacaoInAndDataVencimentoBetween(
            Collection<SituacaoContaReceber> situacoes, LocalDate inicio, LocalDate fim);
    @EntityGraph(attributePaths = "cliente")
    List<ContaReceber> findByOrganizacaoIdAndSituacaoInAndDataVencimentoBetween(
            Long organizacaoId, Collection<SituacaoContaReceber> situacoes,
            LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = {"cliente", "notaSaida", "notaServico", "formaPagamento"})
    Page<ContaReceber> findAllByOrganizacaoId(Long organizacaoId, Pageable pageable);
    @EntityGraph(attributePaths = {"cliente", "notaSaida", "notaServico", "formaPagamento"})
    Page<ContaReceber> findAllByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "notaSaida", "notaServico", "formaPagamento"})
    Optional<ContaReceber> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    @EntityGraph(attributePaths = {"cliente", "notaSaida", "notaServico", "formaPagamento"})
    Optional<ContaReceber> findByIdAndOrganizacaoIdIsNull(Long id);
}
