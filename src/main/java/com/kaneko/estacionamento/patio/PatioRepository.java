package com.kaneko.estacionamento.patio;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link Patio}. Filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega a cidade via
 * {@link EntityGraph}.
 */
public interface PatioRepository extends JpaRepository<Patio, Long>,
        JpaSpecificationExecutor<Patio> {

    @Override
    @EntityGraph(attributePaths = {"cidade"})
    @Query("select p from Patio p where p.id = :id and p.organizacaoId is null")
    Optional<Patio> findById(@Param("id") Long id);
}
