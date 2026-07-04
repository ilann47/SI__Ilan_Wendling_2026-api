package com.kaneko.estacionamento.fornecedor;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Fornecedor}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega as referencias e as
 * colecoes de contato via {@link EntityGraph}.
 */
public interface FornecedorRepository extends JpaRepository<Fornecedor, Long>,
        JpaSpecificationExecutor<Fornecedor> {

    boolean existsByDocumento(String documento);

    boolean existsByDocumentoAndIdNot(String documento, Long id);

    @Override
    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento", "emails", "telefones"})
    Optional<Fornecedor> findById(Long id);
}
