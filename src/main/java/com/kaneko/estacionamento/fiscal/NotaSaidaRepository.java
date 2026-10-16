package com.kaneko.estacionamento.fiscal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link NotaSaida}. As consultas carregam os itens (e seus
 * produtos) e o cliente via {@link EntityGraph}.
 */
public interface NotaSaidaRepository extends JpaRepository<NotaSaida, Long> {
    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque",
            "vendaAdministrativa", "itens", "itens.produto"})
    Optional<NotaSaida> findByOrganizacaoIdAndVendaAdministrativaId(Long organizacaoId, Long vendaAdministrativaId);

    boolean existsByOrganizacaoIdAndVendaAdministrativaId(
            Long organizacaoId, Long vendaAdministrativaId);

    boolean existsByOrganizacaoIdAndNumeroAndSerieAndClienteId(
            Long organizacaoId, String numero, String serie, Long clienteId);
    boolean existsByOrganizacaoIdIsNullAndNumeroAndSerieAndClienteId(
            String numero, String serie, Long clienteId);

    /** Notas de saida numa situacao, emitidas no intervalo — base do faturamento (4.27). */
    List<NotaSaida> findByOrganizacaoIdIsNullAndSituacaoAndDataEmissaoBetween(
            SituacaoNota situacao, LocalDate inicio, LocalDate fim);
    List<NotaSaida> findByOrganizacaoIdAndSituacaoAndDataEmissaoBetween(
            Long organizacaoId, SituacaoNota situacao, LocalDate inicio, LocalDate fim);

    @Query(value = "select n.id from NotaSaida n where n.organizacaoId = :organizacaoId",
            countQuery = "select count(n.id) from NotaSaida n where n.organizacaoId = :organizacaoId")
    Page<Long> findIdsByOrganizacaoId(@Param("organizacaoId") Long organizacaoId,
            Pageable pageable);
    @Query(value = "select n.id from NotaSaida n where n.organizacaoId is null",
            countQuery = "select count(n.id) from NotaSaida n where n.organizacaoId is null")
    Page<Long> findIdsByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque",
            "vendaAdministrativa", "itens", "itens.produto"})
    List<NotaSaida> findAllByIdIn(List<Long> ids);

    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque",
            "vendaAdministrativa", "itens", "itens.produto"})
    Optional<NotaSaida> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque",
            "vendaAdministrativa", "itens", "itens.produto"})
    Optional<NotaSaida> findByIdAndOrganizacaoIdIsNull(Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select n from NotaSaida n where n.id=:id and n.organizacaoId=:organizacaoId")
    Optional<NotaSaida> findParaAtualizar(Long id, Long organizacaoId);
}
