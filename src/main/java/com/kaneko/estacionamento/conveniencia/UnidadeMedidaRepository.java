package com.kaneko.estacionamento.conveniencia;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link UnidadeMedida}. As operacoes basicas de CRUD e
 * paginacao sao herdadas de {@link JpaRepository}.
 */
public interface UnidadeMedidaRepository extends JpaRepository<UnidadeMedida, Long> {

    /** Indica se ja existe uma unidade com a sigla informada (unicidade uq_unidade_sigla). */
    boolean existsBySigla(String sigla);

    /** Indica se existe OUTRA unidade (id diferente) com a mesma sigla — usado em alteracoes. */
    boolean existsBySiglaAndIdNot(String sigla, Long id);
}
