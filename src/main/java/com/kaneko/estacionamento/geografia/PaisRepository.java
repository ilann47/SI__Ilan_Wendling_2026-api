package com.kaneko.estacionamento.geografia;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Pais}. As operacoes basicas de CRUD e paginacao sao
 * herdadas de {@link JpaRepository}.
 */
public interface PaisRepository extends JpaRepository<Pais, Long> {
}
