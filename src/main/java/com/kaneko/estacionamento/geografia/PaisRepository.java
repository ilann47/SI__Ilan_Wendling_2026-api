package com.kaneko.estacionamento.geografia;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Pais}. As operacoes basicas de CRUD e paginacao sao
 * herdadas de {@link JpaRepository}; os filtros dinamicos das listagens vem de
 * {@link JpaSpecificationExecutor}.
 */
public interface PaisRepository extends JpaRepository<Pais, Long>,
        JpaSpecificationExecutor<Pais> {

    /** Indica se ja existe um pais com o nome informado (unicidade uq_pais_nome). */
    boolean existsByNome(String nome);

    /** Indica se existe OUTRO pais (id diferente) com o mesmo nome — usado em alteracoes. */
    boolean existsByNomeAndIdNot(String nome, Long id);

    Optional<Pais> findByNomeIgnoreCase(String nome);
}
