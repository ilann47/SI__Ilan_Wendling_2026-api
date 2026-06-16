package com.kaneko.estacionamento.geografia;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Cidade}. As operacoes basicas de CRUD e paginacao sao
 * herdadas de {@link JpaRepository}.
 */
public interface CidadeRepository extends JpaRepository<Cidade, Long> {
}
