package com.kaneko.estacionamento.pagamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link FormaPagamento}. As operacoes basicas de CRUD e
 * paginacao sao herdadas de {@link JpaRepository}; os filtros dinamicos das
 * listagens vem de {@link JpaSpecificationExecutor}.
 */
public interface FormaPagamentoRepository extends JpaRepository<FormaPagamento, Long>,
        JpaSpecificationExecutor<FormaPagamento> {

    /** Indica se ja existe uma forma de pagamento com o nome informado (unicidade uq_forma_pagamento_nome). */
    boolean existsByNome(String nome);

    /** Indica se existe OUTRA forma de pagamento (id diferente) com o mesmo nome — usado em alteracoes. */
    boolean existsByNomeAndIdNot(String nome, Long id);
}
