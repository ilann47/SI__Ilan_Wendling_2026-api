package com.kaneko.estacionamento.rh;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link Cargo}.
 */
public interface CargoRepository extends JpaRepository<Cargo, Long>,
        JpaSpecificationExecutor<Cargo> {

    Optional<Cargo> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    Optional<Cargo> findByIdAndOrganizacaoIdIsNull(Long id);

    @Query("""
            select count(c) > 0 from Cargo c
            where c.organizacaoId = :organizacaoId
              and lower(trim(c.nome)) = lower(trim(:nome))
              and (:idIgnorado is null or c.id <> :idIgnorado)
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome,
            @Param("idIgnorado") Long idIgnorado);
}
