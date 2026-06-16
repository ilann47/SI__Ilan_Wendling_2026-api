package com.kaneko.estacionamento.geografia;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Cidade}. As consultas carregam o {@code estado}
 * associado via {@link EntityGraph} para a montagem da resposta sem lazy
 * loading.
 */
public interface CidadeRepository extends JpaRepository<Cidade, Long> {

    /** Unicidade do nome dentro do estado (uq_cidade_nome_estado). */
    boolean existsByEstadoIdAndNome(Long estadoId, String nome);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByEstadoIdAndNomeAndIdNot(Long estadoId, String nome, Long id);

    @Override
    @EntityGraph(attributePaths = "estado")
    Page<Cidade> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "estado")
    Optional<Cidade> findById(Long id);
}
