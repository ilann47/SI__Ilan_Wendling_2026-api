package com.kaneko.estacionamento.conveniencia;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link UnidadeMedida}. As operacoes basicas de CRUD e
 * paginacao sao herdadas de {@link JpaRepository}; os filtros dinamicos das
 * listagens usam {@link JpaSpecificationExecutor}.
 */
public interface UnidadeMedidaRepository extends JpaRepository<UnidadeMedida, Long>,
        JpaSpecificationExecutor<UnidadeMedida> {

    /** Indica se ja existe uma unidade com a sigla informada (unicidade uq_unidade_sigla). */
    Optional<UnidadeMedida> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    Optional<UnidadeMedida> findByIdAndOrganizacaoIdIsNull(Long id);

    /** Indica se existe OUTRA unidade (id diferente) com a mesma sigla — usado em alteracoes. */
    @Query("""
            select count(u) > 0 from UnidadeMedida u
            where u.organizacaoId = :organizacaoId
              and upper(trim(u.sigla)) = upper(trim(:sigla))
              and (:idIgnorado is null or u.id <> :idIgnorado)
            """)
    boolean existeSiglaNormalizada(
            @Param("organizacaoId") Long organizacaoId,
            @Param("sigla") String sigla,
            @Param("idIgnorado") Long idIgnorado);

    @Query("""
            select count(u) > 0 from UnidadeMedida u
            where u.organizacaoId = :organizacaoId
              and lower(trim(u.nome)) = lower(trim(:nome))
              and (:idIgnorado is null or u.id <> :idIgnorado)
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome,
            @Param("idIgnorado") Long idIgnorado);
}
