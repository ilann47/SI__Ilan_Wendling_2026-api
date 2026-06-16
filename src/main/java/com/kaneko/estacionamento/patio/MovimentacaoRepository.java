package com.kaneko.estacionamento.patio;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Movimentacao}. As consultas carregam veiculo,
 * mensalista e tarifa via {@link EntityGraph}.
 */
public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    /** Ha movimentacao com o status informado para o veiculo? (regra 1 ABERTA por veiculo). */
    boolean existsByVeiculoIdAndStatus(Long veiculoId, StatusMovimentacao status);

    @EntityGraph(attributePaths = {"veiculo", "mensalista", "tarifa"})
    Page<Movimentacao> findByStatus(StatusMovimentacao status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"veiculo", "mensalista", "tarifa"})
    Page<Movimentacao> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"veiculo", "mensalista", "tarifa"})
    Optional<Movimentacao> findById(Long id);
}
