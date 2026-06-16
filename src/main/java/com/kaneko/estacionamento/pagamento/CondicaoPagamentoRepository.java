package com.kaneko.estacionamento.pagamento;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link CondicaoPagamento}. As consultas carregam as
 * {@code parcelas} (e a forma de pagamento de cada parcela) via
 * {@link EntityGraph} para permitir a montagem da resposta fora da transacao
 * sem lazy loading.
 */
public interface CondicaoPagamentoRepository extends JpaRepository<CondicaoPagamento, Long> {

    /** Indica se ja existe uma condicao com o nome informado (uq_condicao_pagamento_nome). */
    boolean existsByNome(String nome);

    /** Indica se existe OUTRA condicao (id diferente) com o mesmo nome — usado em alteracoes. */
    boolean existsByNomeAndIdNot(String nome, Long id);

    @Override
    @EntityGraph(attributePaths = {"parcelas", "parcelas.formaPagamento"})
    Page<CondicaoPagamento> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"parcelas", "parcelas.formaPagamento"})
    Optional<CondicaoPagamento> findById(Long id);
}
