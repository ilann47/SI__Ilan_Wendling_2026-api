package com.kaneko.estacionamento.evento;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoRepository extends JpaRepository<Evento, Long> {
    Optional<Evento> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Query("""
            select e.status from Evento e
            where e.id = :id and e.organizacaoId = :organizacaoId
            """)
    Optional<StatusEvento> findStatusByIdAndOrganizacaoId(
            @Param("id") Long id,
            @Param("organizacaoId") Long organizacaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from Evento e
            where e.id = :id and e.organizacaoId = :organizacaoId
            """)
    Optional<Evento> findByIdAndOrganizacaoIdForUpdate(
            @Param("id") Long id, @Param("organizacaoId") Long organizacaoId);

    boolean existsByOrganizacaoIdAndExternalIdIgnoreCase(Long organizacaoId, String externalId);
}
