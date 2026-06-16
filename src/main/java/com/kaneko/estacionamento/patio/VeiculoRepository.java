package com.kaneko.estacionamento.patio;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Veiculo}. As consultas carregam o {@code cliente}
 * associado (quando houver) via {@link EntityGraph}.
 */
public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {

    /** Unicidade da placa (uq_veiculo_placa). */
    boolean existsByPlaca(String placa);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByPlacaAndIdNot(String placa, Long id);

    @Override
    @EntityGraph(attributePaths = "cliente")
    Page<Veiculo> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "cliente")
    Optional<Veiculo> findById(Long id);
}
