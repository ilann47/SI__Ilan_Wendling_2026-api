package com.kaneko.estacionamento.vendas;

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

public interface VendaAdministrativaRepository
        extends JpaRepository<VendaAdministrativa, Long> {
    boolean existsByOrganizacaoIdAndNumeroIgnoreCase(Long organizacaoId, String numero);
    boolean existsByOrganizacaoIdAndNumeroIgnoreCaseAndIdNot(
            Long organizacaoId, String numero, Long id);

    @Query("select v.id from VendaAdministrativa v where v.organizacaoId=:organizacaoId")
    Page<Long> findPageIdsByOrganizacaoId(@Param("organizacaoId") Long organizacaoId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque",
            "itens", "itens.produto"})
    List<VendaAdministrativa> findAllByOrganizacaoIdAndIdIn(
            Long organizacaoId, Collection<Long> ids);

    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "localEstoque",
            "itens", "itens.produto"})
    Optional<VendaAdministrativa> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VendaAdministrativa v where v.id=:id and v.organizacaoId=:organizacaoId")
    Optional<VendaAdministrativa> findParaAtualizar(@Param("id") Long id,
            @Param("organizacaoId") Long organizacaoId);
}
