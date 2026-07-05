package com.kaneko.estacionamento.logistica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link VeiculoFrota}. Filtros dinamicos via
 * {@link JpaSpecificationExecutor}; placa unica.
 */
public interface VeiculoFrotaRepository extends JpaRepository<VeiculoFrota, Long>,
        JpaSpecificationExecutor<VeiculoFrota> {

    boolean existsByPlaca(String placa);

    boolean existsByPlacaAndIdNot(String placa, Long id);
}
