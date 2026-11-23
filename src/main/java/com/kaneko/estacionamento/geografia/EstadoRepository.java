package com.kaneko.estacionamento.geografia;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Estado}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega o {@code pais}
 * associado via {@link EntityGraph} para a montagem da resposta sem lazy
 * loading.
 */
public interface EstadoRepository extends JpaRepository<Estado, Long>,
        JpaSpecificationExecutor<Estado> {

    /** Unicidade de UF dentro do pais (uq_estado_uf_pais). */
    boolean existsByPaisIdAndUf(Long paisId, String uf);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByPaisIdAndUfAndIdNot(Long paisId, String uf, Long id);

    Optional<Estado> findByPaisIdAndUfIgnoreCase(Long paisId, String uf);

    @Override
    @EntityGraph(attributePaths = "pais")
    Optional<Estado> findById(Long id);
}
