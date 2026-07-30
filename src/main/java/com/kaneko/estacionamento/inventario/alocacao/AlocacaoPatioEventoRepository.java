package com.kaneko.estacionamento.inventario.alocacao;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlocacaoPatioEventoRepository
        extends JpaRepository<AlocacaoPatioEvento, Long> {

    Optional<AlocacaoPatioEvento> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    boolean existsByEventoIdAndPatioId(Long eventoId, Long patioId);

    @Query("""
            select coalesce(sum(a.capacidadeOperacional), 0)
            from AlocacaoPatioEvento a
            where a.patioId = :patioId
              and a.inicio < :fim and a.fim > :inicio
              and (:excluirId is null or a.id <> :excluirId)
            """)
    Long somarCapacidadeOperacionalSobreposta(
            @Param("patioId") Long patioId,
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim,
            @Param("excluirId") Long excluirId);
}
