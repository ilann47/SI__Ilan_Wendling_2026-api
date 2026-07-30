package com.kaneko.estacionamento.inventario.reserva;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaInventarioRepository extends JpaRepository<ReservaInventario, Long> {

    Optional<ReservaInventario> findByIdAndOrganizacaoIdAndAtorId(
            Long id, Long organizacaoId, Long atorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from ReservaInventario r
            where r.id = :id and r.organizacaoId = :organizacaoId
              and r.atorId = :atorId
            """)
    Optional<ReservaInventario> findDoAtorForUpdate(
            @Param("id") Long id, @Param("organizacaoId") Long organizacaoId,
            @Param("atorId") Long atorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from ReservaInventario r
            where r.produtoId = :produtoId
              and r.status = :status and r.expiraEm <= :instante
            order by r.id
            """)
    List<ReservaInventario> buscarExpiradasDoProdutoForUpdate(
            @Param("produtoId") Long produtoId,
            @Param("status") StatusReservaInventario status,
            @Param("instante") OffsetDateTime instante);

    @Query("""
            select r.id from ReservaInventario r
            where r.status = :status and r.expiraEm <= :instante
            order by r.id
            """)
    List<Long> buscarIdsExpirados(
            @Param("status") StatusReservaInventario status,
            @Param("instante") OffsetDateTime instante,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservaInventario r where r.id = :id")
    Optional<ReservaInventario> findByIdForUpdate(@Param("id") Long id);
}
