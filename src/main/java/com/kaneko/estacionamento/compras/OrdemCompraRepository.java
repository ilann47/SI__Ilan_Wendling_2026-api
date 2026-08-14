package com.kaneko.estacionamento.compras;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrdemCompraRepository extends JpaRepository<OrdemCompra, Long> {

    @Query("select o.id from OrdemCompra o where o.organizacaoId=:organizacaoId")
    Page<Long> findPageIdsByOrganizacaoId(Long organizacaoId, Pageable pageable);

    @EntityGraph(attributePaths = {"fornecedor", "itens", "itens.produto"})
    List<OrdemCompra> findAllByOrganizacaoIdAndIdIn(Long organizacaoId, List<Long> ids);

    @EntityGraph(attributePaths = {"fornecedor", "itens", "itens.produto"})
    Optional<OrdemCompra> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    boolean existsByOrganizacaoIdAndNumeroIgnoreCaseAndIdNot(
            Long organizacaoId, String numero, Long id);

    boolean existsByOrganizacaoIdAndNumeroIgnoreCase(Long organizacaoId, String numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"fornecedor", "itens", "itens.produto"})
    @Query("select o from OrdemCompra o where o.id=:id and o.organizacaoId=:organizacaoId")
    Optional<OrdemCompra> findParaAtualizar(Long id, Long organizacaoId);
}
