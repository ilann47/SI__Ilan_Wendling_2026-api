package com.kaneko.estacionamento.pagamento;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link FormaPagamento}. As operacoes basicas de CRUD e
 * paginacao sao herdadas de {@link JpaRepository}; os filtros dinamicos das
 * listagens vem de {@link JpaSpecificationExecutor}.
 */
public interface FormaPagamentoRepository extends JpaRepository<FormaPagamento, Long>,
        JpaSpecificationExecutor<FormaPagamento> {

    Optional<FormaPagamento> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @Query("""
            select count(f) > 0 from FormaPagamento f
            where f.organizacaoId = :organizacaoId
              and lower(trim(f.nome)) = lower(trim(:nome))
              and (:idIgnorado is null or f.id <> :idIgnorado)
            """)
    boolean existeNomeNormalizado(
            @Param("organizacaoId") Long organizacaoId,
            @Param("nome") String nome,
            @Param("idIgnorado") Long idIgnorado);
}
