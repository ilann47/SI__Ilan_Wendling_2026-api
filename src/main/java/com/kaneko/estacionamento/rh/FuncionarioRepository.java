package com.kaneko.estacionamento.rh;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Funcionario}. As consultas carregam cargo, cidade e
 * nacionalidade via {@link EntityGraph}.
 */
public interface FuncionarioRepository extends JpaRepository<Funcionario, Long> {

    /** Unicidade do CPF (uq_funcionario_cpf). */
    boolean existsByCpf(String cpf);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByCpfAndIdNot(String cpf, Long id);

    @Override
    @EntityGraph(attributePaths = {"cargo", "cidade", "nacionalidade"})
    Page<Funcionario> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cargo", "cidade", "nacionalidade"})
    Optional<Funcionario> findById(Long id);
}
