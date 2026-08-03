package com.kaneko.estacionamento.conveniencia;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link Categoria}. As operacoes basicas de CRUD e paginacao
 * sao herdadas de {@link JpaRepository}; os filtros dinamicos das listagens
 * usam {@link JpaSpecificationExecutor}.
 */
public interface CategoriaRepository extends JpaRepository<Categoria, Long>,
        JpaSpecificationExecutor<Categoria> {

    /** Indica se ja existe uma categoria com o nome informado (unicidade uq_categoria_nome). */
    Optional<Categoria> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    Optional<Categoria> findByIdAndOrganizacaoIdIsNull(Long id);

    /** Indica se existe OUTRA categoria (id diferente) com o mesmo nome — usado em alteracoes. */
    @Query("""
            select count(c) > 0 from Categoria c
            where c.organizacaoId = :organizacaoId
              and lower(trim(c.nome)) = lower(trim(:nome))
              and (:idIgnorado is null or c.id <> :idIgnorado)
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome,
            @Param("idIgnorado") Long idIgnorado);
}
