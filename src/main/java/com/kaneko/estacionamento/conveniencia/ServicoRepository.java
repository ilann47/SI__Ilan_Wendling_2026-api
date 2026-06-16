package com.kaneko.estacionamento.conveniencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Servico}. As operacoes basicas de CRUD e paginacao sao
 * herdadas de {@link JpaRepository}; os filtros dinamicos das listagens usam
 * {@link JpaSpecificationExecutor}.
 */
public interface ServicoRepository extends JpaRepository<Servico, Long>,
        JpaSpecificationExecutor<Servico> {

    /** Indica se ja existe um servico com o nome informado (unicidade uq_servico_nome). */
    boolean existsByNome(String nome);

    /** Indica se existe OUTRO servico (id diferente) com o mesmo nome — usado em alteracoes. */
    boolean existsByNomeAndIdNot(String nome, Long id);
}
