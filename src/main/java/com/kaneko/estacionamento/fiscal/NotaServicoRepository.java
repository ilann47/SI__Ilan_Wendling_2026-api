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

    boolean existsByOrganizacaoIdAndNumeroAndSerie(Long organizacaoId, String numero, String serie);
    boolean existsByOrganizacaoIdIsNullAndNumeroAndSerie(String numero, String serie);

    /** Notas de servico numa situacao, emitidas no intervalo — base do faturamento (4.27). */
    List<NotaServico> findBySituacaoAndDataEmissaoBetween(
            SituacaoNotaServico situacao, LocalDate inicio, LocalDate fim);
    List<NotaServico> findByOrganizacaoIdAndSituacaoAndDataEmissaoBetween(
            Long organizacaoId, SituacaoNotaServico situacao, LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento"})
    Page<NotaServico> findAllByOrganizacaoId(Long organizacaoId, Pageable pageable);
    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento"})
    Page<NotaServico> findAllByOrganizacaoIdIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento"})
    Optional<NotaServico> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    @EntityGraph(attributePaths = {"cliente", "servico", "formaPagamento"})
    Optional<NotaServico> findByIdAndOrganizacaoIdIsNull(Long id);
}
