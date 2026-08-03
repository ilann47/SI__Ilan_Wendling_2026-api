package com.kaneko.estacionamento.conveniencia;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link Marca}. As operacoes basicas de CRUD e paginacao sao
 * herdadas de {@link JpaRepository}; os filtros dinamicos das listagens usam
 * {@link JpaSpecificationExecutor}.
 */
public interface MarcaRepository extends JpaRepository<Marca, Long>,
        JpaSpecificationExecutor<Marca> {

    /** Indica se ja existe uma marca com o nome informado (unicidade uq_marca_nome). */
    Optional<Marca> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    Optional<Marca> findByIdAndOrganizacaoIdIsNull(Long id);

    /** Indica se existe OUTRA marca (id diferente) com o mesmo nome — usado em alteracoes. */
    @Query("""
            select count(m) > 0 from Marca m
            where m.organizacaoId = :organizacaoId
              and lower(trim(m.nome)) = lower(trim(:nome))
              and (:idIgnorado is null or m.id <> :idIgnorado)
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome,
            @Param("idIgnorado") Long idIgnorado);
}
