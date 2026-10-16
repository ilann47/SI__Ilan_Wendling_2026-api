package com.kaneko.estacionamento.fiscal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link NotaServico}.
 */
public interface NotaServicoRepository extends JpaRepository<NotaServico, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select n from NotaServico n where n.id=:id and "
            + "((:org is null and n.organizacaoId is null) or n.organizacaoId=:org)")
    Optional<NotaServico> findParaAtualizar(Long id, Long org);

    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento", "ordemServico"})
    Optional<NotaServico> findByOrganizacaoIdAndOrdemServicoId(Long organizacaoId, Long ordemServicoId);

    boolean existsByOrganizacaoIdAndNumeroAndSerie(Long organizacaoId, String numero, String serie);
    boolean existsByOrganizacaoIdIsNullAndNumeroAndSerie(String numero, String serie);
    boolean existsByOrdemServicoId(Long ordemServicoId);

    /** Notas de servico numa situacao, emitidas no intervalo — base do faturamento (4.27). */
    List<NotaServico> findByOrganizacaoIdIsNullAndSituacaoAndDataEmissaoBetween(
            SituacaoNotaServico situacao, LocalDate inicio, LocalDate fim);
    List<NotaServico> findByOrganizacaoIdAndSituacaoAndDataEmissaoBetween(
            Long organizacaoId, SituacaoNotaServico situacao, LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento", "ordemServico"})
    Page<NotaServico> findAllByOrganizacaoId(Long organizacaoId, Pageable pageable);
    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento"})
    Page<NotaServico> findAllByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento", "ordemServico"})
    Optional<NotaServico> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento"})
    Optional<NotaServico> findByIdAndOrganizacaoIdIsNull(Long id);
}
