package com.kaneko.estacionamento.geografia;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Estado}. As consultas carregam o {@code pais}
 * associado via {@link EntityGraph} para permitir a montagem da resposta fora
 * da transacao sem lazy loading.
 */
public interface EstadoRepository extends JpaRepository<Estado, Long> {

    /** Unicidade de UF dentro do pais (uq_estado_uf_pais). */
    boolean existsByPaisIdAndUf(Long paisId, String uf);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByPaisIdAndUfAndIdNot(Long paisId, String uf, Long id);

    @Override
    @EntityGraph(attributePaths = "pais")
    Page<Estado> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "pais")
    Optional<Estado> findById(Long id);
}
