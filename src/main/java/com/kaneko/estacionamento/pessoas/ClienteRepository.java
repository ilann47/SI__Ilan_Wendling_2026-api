package com.kaneko.estacionamento.pessoas;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link Cliente}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega cidade, nacionalidade
 * e condicao de pagamento via {@link EntityGraph}.
 */
public interface ClienteRepository extends JpaRepository<Cliente, Long>,
        JpaSpecificationExecutor<Cliente> {

    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento"})
    Optional<Cliente> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento"})
    Optional<Cliente> findByIdAndOrganizacaoIdIsNull(Long id);

    @Query("""
            select count(c) > 0 from Cliente c
            where c.organizacaoId = :organizacaoId
              and c.documento = :documento
              and (:idIgnorado is null or c.id <> :idIgnorado)
            """)
    boolean existeDocumento(
            @Param("organizacaoId") Long organizacaoId,
            @Param("documento") String documento,
            @Param("idIgnorado") Long idIgnorado);

    @Override
    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento"})
    Optional<Cliente> findById(Long id);
}
