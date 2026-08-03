package com.kaneko.estacionamento.rh;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio tenant-aware de {@link Funcionario}. */
public interface FuncionarioRepository extends JpaRepository<Funcionario, Long>,
        JpaSpecificationExecutor<Funcionario> {

    @EntityGraph(attributePaths = {"cargo", "cidade"})
    Optional<Funcionario> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"cargo", "cidade"})
    Optional<Funcionario> findByIdAndOrganizacaoIdIsNull(Long id);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM funcionario f
                WHERE f.organizacao_id = :organizacaoId
                  AND regexp_replace(f.cpf, '[^0-9]', '', 'g') = :cpf
                  AND (CAST(:idIgnorado AS BIGINT) IS NULL OR f.id <> :idIgnorado)
            )
            """, nativeQuery = true)
    boolean existeCpf(
            @Param("organizacaoId") Long organizacaoId,
            @Param("cpf") String cpf,
            @Param("idIgnorado") Long idIgnorado);

    @Override
    @EntityGraph(attributePaths = {"cargo", "cidade"})
    Optional<Funcionario> findById(Long id);
}
