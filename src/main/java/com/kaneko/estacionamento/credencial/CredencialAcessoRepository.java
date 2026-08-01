package com.kaneko.estacionamento.credencial;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredencialAcessoRepository
        extends JpaRepository<CredencialAcesso, Long> {
    Optional<CredencialAcesso> findByIdAndOrganizacaoId(
            Long id, Long organizacaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from CredencialAcesso c
            where c.id = :id and c.organizacaoId = :organizacaoId
            """)
    Optional<CredencialAcesso> findByIdAndOrganizacaoIdForUpdate(
            @Param("id") Long id,
            @Param("organizacaoId") Long organizacaoId);

    Optional<CredencialAcesso> findByIdAndOrganizacaoIdAndAtorId(
            Long id, Long organizacaoId, Long atorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from CredencialAcesso c
            where c.id = :id and c.organizacaoId = :organizacaoId
              and c.atorId = :atorId
            """)
    Optional<CredencialAcesso> findDoAtorForUpdate(
            @Param("id") Long id,
            @Param("organizacaoId") Long organizacaoId,
            @Param("atorId") Long atorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from CredencialAcesso c
            where c.pedidoId = :pedidoId and c.organizacaoId = :organizacaoId
            order by c.id
            """)
    List<CredencialAcesso> findDoPedidoForUpdate(
            @Param("pedidoId") Long pedidoId,
            @Param("organizacaoId") Long organizacaoId);

    long countByPedidoId(Long pedidoId);

    long countByItemPedidoId(Long itemPedidoId);
}
