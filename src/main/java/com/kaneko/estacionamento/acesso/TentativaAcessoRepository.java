package com.kaneko.estacionamento.acesso;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TentativaAcessoRepository
        extends JpaRepository<TentativaAcesso, Long> {
    Optional<TentativaAcesso> findByIdAndOrganizacaoIdAndAtorId(
            Long id, Long organizacaoId, Long atorId);

    @Query("""
            select tentativa
            from TentativaAcesso tentativa
            where tentativa.organizacaoId = :organizacaoId
              and (:cursor is null or tentativa.id < :cursor)
              and (:eventoId is null or tentativa.eventoId = :eventoId)
              and (:decisao is null or tentativa.status = :decisao)
              and (:motivo is null or tentativa.motivo = :motivo)
            order by tentativa.id desc
            """)
    List<TentativaAcesso> buscarFeed(
            @Param("organizacaoId") Long organizacaoId,
            @Param("cursor") Long cursor,
            @Param("eventoId") Long eventoId,
            @Param("decisao") StatusTentativaAcesso decisao,
            @Param("motivo") MotivoDecisaoAcesso motivo,
            Pageable pageable);
}
