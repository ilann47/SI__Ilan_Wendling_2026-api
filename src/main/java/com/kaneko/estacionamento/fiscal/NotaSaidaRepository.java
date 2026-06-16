package com.kaneko.estacionamento.fiscal;

import java.time.LocalDate;
import java.util.List;
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

    /** Notas de saida numa situacao, emitidas no intervalo — base do faturamento (4.27). */
    List<NotaSaida> findBySituacaoAndDataEmissaoBetween(
            SituacaoNota situacao, LocalDate inicio, LocalDate fim);

    @Override
    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "itens", "itens.produto"})
    Page<NotaSaida> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cliente", "condicaoPagamento", "itens", "itens.produto"})
    Optional<NotaSaida> findById(Long id);
}
