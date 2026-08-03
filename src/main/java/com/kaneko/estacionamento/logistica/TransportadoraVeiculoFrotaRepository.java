package com.kaneko.estacionamento.logistica;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio por tenant; espelha {@code uq_tvf_organizacao_par}. */
public interface TransportadoraVeiculoFrotaRepository
        extends JpaRepository<TransportadoraVeiculoFrota, Long> {

    @EntityGraph(attributePaths = {"transportadora", "veiculoFrota"})
    Page<TransportadoraVeiculoFrota> findByOrganizacaoId(
            Long organizacaoId, Pageable pageable);

    @EntityGraph(attributePaths = {"transportadora", "veiculoFrota"})
    Optional<TransportadoraVeiculoFrota> findByIdAndOrganizacaoId(
            Long id, Long organizacaoId);

    @Query("""
            select count(v) > 0 from TransportadoraVeiculoFrota v
            where v.organizacaoId = :organizacaoId
              and v.transportadora.id = :transportadoraId
              and v.veiculoFrota.id = :veiculoFrotaId
              and (:idIgnorado is null or v.id <> :idIgnorado)
            """)
    boolean existePar(
            @Param("organizacaoId") Long organizacaoId,
            @Param("transportadoraId") Long transportadoraId,
            @Param("veiculoFrotaId") Long veiculoFrotaId,
            @Param("idIgnorado") Long idIgnorado);
}
