package com.kaneko.estacionamento.fornecedor;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Fornecedor}. As consultas carregam as referencias e as
 * colecoes de contato via {@link EntityGraph}.
 */
public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {

    boolean existsByDocumento(String documento);

    boolean existsByDocumentoAndIdNot(String documento, Long id);

    @Override
    @EntityGraph(attributePaths = {"cidade", "nacionalidade", "condicaoPagamento", "emails", "telefones"})
    Page<Fornecedor> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cidade", "nacionalidade", "condicaoPagamento", "emails", "telefones"})
    Optional<Fornecedor> findById(Long id);
}
