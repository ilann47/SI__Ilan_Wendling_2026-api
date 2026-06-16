package com.kaneko.estacionamento.fiscal;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link NotaSaida}. As consultas carregam os itens (e seus
 * produtos) e o cliente via {@link EntityGraph}.
 */
public interface NotaSaidaRepository extends JpaRepository<NotaSaida, Long> {

    boolean existsByNumeroAndSerieAndClienteId(String numero, String serie, Long clienteId);

    @Override
    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "itens", "itens.produto"})
    Page<NotaSaida> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "itens", "itens.produto"})
    Optional<NotaSaida> findById(Long id);
}
