package com.kaneko.estacionamento.patio;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Tarifa}. Operacoes de CRUD e paginacao herdadas de
 * {@link JpaRepository}.
 */
public interface TarifaRepository extends JpaRepository<Tarifa, Long> {
}
