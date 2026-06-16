package com.kaneko.estacionamento.pessoas;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Cliente}. As consultas carregam cidade, nacionalidade e
 * condicao de pagamento via {@link EntityGraph} para a montagem da resposta sem
 * lazy loading.
 */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /** Unicidade do documento (uq_cliente_documento). */
    boolean existsByDocumento(String documento);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByDocumentoAndIdNot(String documento, Long id);

    @Override
    @EntityGraph(attributePaths = {"cidade", "nacionalidade", "condicaoPagamento"})
    Page<Cliente> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cidade", "nacionalidade", "condicaoPagamento"})
    Optional<Cliente> findById(Long id);
}
