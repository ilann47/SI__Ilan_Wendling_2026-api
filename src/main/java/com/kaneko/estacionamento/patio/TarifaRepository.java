package com.kaneko.estacionamento.patio;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Tarifa}. Operacoes de CRUD e paginacao herdadas de
 * {@link JpaRepository}; suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}.
 */
public interface TarifaRepository extends JpaRepository<Tarifa, Long>,
        JpaSpecificationExecutor<Tarifa> {

    /**
     * Tarifa vigente para a cobranca: a tarifa ativa mais recente (maior id).
     *
     * <p>O schema nao garante uma unica tarifa ativa; operacionalmente
     * espera-se uma so, e o desempate adota a mais recente.
     */
    Optional<Tarifa> findFirstByAtivoTrueOrderByIdDesc();
}
