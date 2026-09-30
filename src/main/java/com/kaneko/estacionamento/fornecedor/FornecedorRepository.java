package com.kaneko.estacionamento.fornecedor;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link Fornecedor}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega as referencias e as
 * colecoes de contato via {@link EntityGraph}.
 */
public interface FornecedorRepository extends JpaRepository<Fornecedor, Long>,
        JpaSpecificationExecutor<Fornecedor> {

    Optional<Fornecedor> findByOrganizacaoIdAndDocumento(Long organizacaoId, String documento);

    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento", "transportadora", "emails", "telefones"})
    Optional<Fornecedor> findByIdAndOrganizacaoId(Long id, Long organizacaoId);

    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento", "transportadora", "emails", "telefones"})
    Optional<Fornecedor> findByIdAndOrganizacaoIdIsNull(Long id);

    @Query("""
            select count(f) > 0 from Fornecedor f
            where f.organizacaoId = :organizacaoId
              and f.documento = :documento
              and (:idIgnorado is null or f.id <> :idIgnorado)
            """)
    boolean existeDocumento(
            @Param("organizacaoId") Long organizacaoId,
            @Param("documento") String documento,
            @Param("idIgnorado") Long idIgnorado);

    @Override
    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento", "transportadora", "emails", "telefones"})
    Optional<Fornecedor> findById(Long id);
}
