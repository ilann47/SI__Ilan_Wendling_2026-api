package com.kaneko.estacionamento.logistica;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link Transportadora}. Filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega cidade e condicao de
 * pagamento via {@link EntityGraph}.
 */
public interface TransportadoraRepository extends JpaRepository<Transportadora, Long>,
        JpaSpecificationExecutor<Transportadora> {

    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento"})
    Optional<Transportadora> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento"})
    Optional<Transportadora> findByIdAndOrganizacaoIdIsNull(Long id);

    @Query("""
            select count(t) > 0 from Transportadora t
            where t.organizacaoId = :organizacaoId
              and t.documento = :documento
              and (:idIgnorado is null or t.id <> :idIgnorado)
            """)
    boolean existeDocumento(
            @Param("organizacaoId") Long organizacaoId,
            @Param("documento") String documento,
            @Param("idIgnorado") Long idIgnorado);

    @Override
    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento"})
    Optional<Transportadora> findById(Long id);
}
