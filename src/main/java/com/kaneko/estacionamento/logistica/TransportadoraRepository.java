package com.kaneko.estacionamento.logistica;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Transportadora}. Filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega cidade e condicao de
 * pagamento via {@link EntityGraph}.
 */
public interface TransportadoraRepository extends JpaRepository<Transportadora, Long>,
        JpaSpecificationExecutor<Transportadora> {

    boolean existsByDocumento(String documento);

    boolean existsByDocumentoAndIdNot(String documento, Long id);

    @Override
    @EntityGraph(attributePaths = {"cidade", "condicaoPagamento"})
    Optional<Transportadora> findById(Long id);
}
