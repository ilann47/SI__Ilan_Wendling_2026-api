package com.kaneko.estacionamento.pessoas;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Cliente}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega cidade, nacionalidade
 * e condicao de pagamento via {@link EntityGraph}.
 */
public interface ClienteRepository extends JpaRepository<Cliente, Long>,
        JpaSpecificationExecutor<Cliente> {

    /** Unicidade do documento (uq_cliente_documento). */
    boolean existsByDocumento(String documento);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByDocumentoAndIdNot(String documento, Long id);

    @Override
    @EntityGraph(attributePaths = {"cidade", "nacionalidade", "condicaoPagamento"})
    Optional<Cliente> findById(Long id);
}
