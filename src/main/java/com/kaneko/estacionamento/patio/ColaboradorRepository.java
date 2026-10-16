package com.kaneko.estacionamento.patio;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Colaborador}. E-mail unico por patio; a busca por id
 * carrega o patio via {@link EntityGraph}.
 */
public interface ColaboradorRepository extends JpaRepository<Colaborador, Long>,
        JpaSpecificationExecutor<Colaborador> {

    boolean existsByPatioIdAndEmailIgnoreCase(Long patioId, String email);

    boolean existsByPatioIdAndEmailIgnoreCaseAndIdNot(Long patioId, String email, Long id);

    @Override
    @EntityGraph(attributePaths = {"patio"})
    Optional<Colaborador> findById(Long id);
}
