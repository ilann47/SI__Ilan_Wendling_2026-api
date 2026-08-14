package com.kaneko.estacionamento.ordensservico;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {
    boolean existsByOrganizacaoIdAndNumeroIgnoreCase(Long organizacaoId, String numero);
    boolean existsByOrganizacaoIdAndNumeroIgnoreCaseAndIdNot(
            Long organizacaoId, String numero, Long id);

    @Query("select o.id from OrdemServico o where o.organizacaoId=:organizacaoId")
    Page<Long> findPageIdsByOrganizacaoId(@Param("organizacaoId") Long organizacaoId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "itens", "itens.servico"})
    List<OrdemServico> findAllByOrganizacaoIdAndIdIn(
            Long organizacaoId, Collection<Long> ids);

    @EntityGraph(attributePaths = {"cliente", "itens", "itens.servico"})
    Optional<OrdemServico> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdemServico o where o.id=:id and o.organizacaoId=:organizacaoId")
    Optional<OrdemServico> findParaAtualizar(@Param("id") Long id,
            @Param("organizacaoId") Long organizacaoId);
}
