package com.kaneko.estacionamento.fiscal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link NotaSaida}. As consultas carregam os itens (e seus
 * produtos) e o cliente via {@link EntityGraph}.
 */
public interface NotaSaidaRepository extends JpaRepository<NotaSaida, Long> {

    boolean existsByOrganizacaoIdAndNumeroAndSerieAndClienteId(
            Long organizacaoId, String numero, String serie, Long clienteId);
    boolean existsByOrganizacaoIdIsNullAndNumeroAndSerieAndClienteId(
            String numero, String serie, Long clienteId);

    /** Notas de saida numa situacao, emitidas no intervalo — base do faturamento (4.27). */
    List<NotaSaida> findBySituacaoAndDataEmissaoBetween(
            SituacaoNota situacao, LocalDate inicio, LocalDate fim);
    List<NotaSaida> findByOrganizacaoIdAndSituacaoAndDataEmissaoBetween(
            Long organizacaoId, SituacaoNota situacao, LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque", "itens", "itens.produto"})
    Page<NotaSaida> findAllByOrganizacaoId(Long organizacaoId, Pageable pageable);
    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque", "itens", "itens.produto"})
    Page<NotaSaida> findAllByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque", "itens", "itens.produto"})
    Optional<NotaSaida> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque", "itens", "itens.produto"})
    Optional<NotaSaida> findByIdAndOrganizacaoIdIsNull(Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select n from NotaSaida n where n.id=:id and n.organizacaoId=:organizacaoId")
    Optional<NotaSaida> findParaAtualizar(Long id, Long organizacaoId);
}
