package com.kaneko.estacionamento.patio;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Veiculo}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega o {@code cliente}
 * associado (quando houver) via {@link EntityGraph}.
 */
public interface VeiculoRepository extends JpaRepository<Veiculo, Long>,
        JpaSpecificationExecutor<Veiculo> {

    /** Unicidade da placa (uq_veiculo_placa). */
    boolean existsByPlaca(String placa);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByPlacaAndIdNot(String placa, Long id);

    @Override
    @EntityGraph(attributePaths = "cliente")
    Optional<Veiculo> findById(Long id);
}
