package com.kaneko.estacionamento.logistica;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio do vinculo {@link TransportadoraVeiculoFrota} (frota N:N).
 */
public interface TransportadoraVeiculoFrotaRepository extends JpaRepository<TransportadoraVeiculoFrota, Long> {

    /** Unicidade do par (uq_transp_veiculo_frota). */
    boolean existsByTransportadoraIdAndVeiculoFrotaId(Long transportadoraId, Long veiculoFrotaId);

    boolean existsByTransportadoraIdAndVeiculoFrotaIdAndIdNot(Long transportadoraId, Long veiculoFrotaId, Long id);

    @Override
    @EntityGraph(attributePaths = {"transportadora", "veiculoFrota"})
    Page<TransportadoraVeiculoFrota> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"transportadora", "veiculoFrota"})
    Optional<TransportadoraVeiculoFrota> findById(Long id);
}
