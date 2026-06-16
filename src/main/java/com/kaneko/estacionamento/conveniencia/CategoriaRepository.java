package com.kaneko.estacionamento.conveniencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Categoria}. As operacoes basicas de CRUD e paginacao
 * sao herdadas de {@link JpaRepository}; os filtros dinamicos das listagens
 * usam {@link JpaSpecificationExecutor}.
 */
public interface CategoriaRepository extends JpaRepository<Categoria, Long>,
        JpaSpecificationExecutor<Categoria> {

    /** Indica se ja existe uma categoria com o nome informado (unicidade uq_categoria_nome). */
    boolean existsByNome(String nome);

    /** Indica se existe OUTRA categoria (id diferente) com o mesmo nome — usado em alteracoes. */
    boolean existsByNomeAndIdNot(String nome, Long id);
}
