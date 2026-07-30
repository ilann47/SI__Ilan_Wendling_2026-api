package com.kaneko.estacionamento.pedido;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    Optional<Pedido> findByIdAndOrganizacaoIdAndAtorId(
            Long id, Long organizacaoId, Long atorId);

    Optional<Pedido> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    boolean existsByReservaInventarioId(Long reservaInventarioId);

    @Query("""
            select p.id from Pedido p
            where p.status in :status and p.expiraEm <= :instante
            order by p.id
            """)
    List<Long> buscarIdsExpirados(
            @Param("status") List<StatusPedido> status,
            @Param("instante") OffsetDateTime instante,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Pedido p
            where p.id = :id and p.organizacaoId = :organizacaoId
            """)
    Optional<Pedido> findByIdAndOrganizacaoIdForUpdate(
            @Param("id") Long id, @Param("organizacaoId") Long organizacaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Pedido p
            where p.id = :id and p.organizacaoId = :organizacaoId
              and p.atorId = :atorId
            """)
    Optional<Pedido> findDoAtorForUpdate(
            @Param("id") Long id, @Param("organizacaoId") Long organizacaoId,
            @Param("atorId") Long atorId);
}
