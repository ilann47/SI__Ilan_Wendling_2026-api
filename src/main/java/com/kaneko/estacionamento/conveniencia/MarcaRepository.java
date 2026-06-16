package com.kaneko.estacionamento.conveniencia;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Marca}. As operacoes basicas de CRUD e paginacao sao
 * herdadas de {@link JpaRepository}.
 */
public interface MarcaRepository extends JpaRepository<Marca, Long> {

    /** Indica se ja existe uma marca com o nome informado (unicidade uq_marca_nome). */
    boolean existsByNome(String nome);

    /** Indica se existe OUTRA marca (id diferente) com o mesmo nome — usado em alteracoes. */
    boolean existsByNomeAndIdNot(String nome, Long id);
}
