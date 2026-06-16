package com.kaneko.estacionamento.patio;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Mensalista}. As consultas carregam cliente e veiculo
 * via {@link EntityGraph}.
 */
public interface MensalistaRepository extends JpaRepository<Mensalista, Long> {

    /** Existe contrato com o status informado para o veiculo? (regra 1 ATIVO por veiculo). */
    boolean existsByVeiculoIdAndStatus(Long veiculoId, StatusMensalista status);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByVeiculoIdAndStatusAndIdNot(Long veiculoId, StatusMensalista status, Long id);

    /** Contrato de um veiculo no status informado (ex.: o contrato ATIVO), se houver. */
    Optional<Mensalista> findFirstByVeiculoIdAndStatus(Long veiculoId, StatusMensalista status);

    @Override
    @EntityGraph(attributePaths = {"cliente", "veiculo"})
    Page<Mensalista> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cliente", "veiculo"})
    Optional<Mensalista> findById(Long id);
}
