package com.kaneko.estacionamento.geografia;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Cidade}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega o {@code estado}
 * associado via {@link EntityGraph} para a montagem da resposta sem lazy
 * loading.
 */
public interface CidadeRepository extends JpaRepository<Cidade, Long>,
        JpaSpecificationExecutor<Cidade> {

    /** Unicidade do nome dentro do estado (uq_cidade_nome_estado). */
    boolean existsByEstadoIdAndNome(Long estadoId, String nome);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByEstadoIdAndNomeAndIdNot(Long estadoId, String nome, Long id);

    Optional<Cidade> findByEstadoIdAndNomeIgnoreCase(Long estadoId, String nome);

    @Override
    @EntityGraph(attributePaths = "estado")
    Optional<Cidade> findById(Long id);
}
