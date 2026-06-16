package com.kaneko.estacionamento.rh;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Funcionario}. Suporta filtros dinamicos via
 * {@link JpaSpecificationExecutor}; a busca por id carrega cargo, cidade e
 * nacionalidade via {@link EntityGraph}.
 */
public interface FuncionarioRepository extends JpaRepository<Funcionario, Long>,
        JpaSpecificationExecutor<Funcionario> {

    /** Unicidade do CPF (uq_funcionario_cpf). */
    boolean existsByCpf(String cpf);

    /** Mesma checagem, ignorando o proprio registro — usado em alteracoes. */
    boolean existsByCpfAndIdNot(String cpf, Long id);

    @Override
    @EntityGraph(attributePaths = {"cargo", "cidade", "nacionalidade"})
    Optional<Funcionario> findById(Long id);
}
