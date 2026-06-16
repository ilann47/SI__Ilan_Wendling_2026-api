package com.kaneko.estacionamento.usuario;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Usuario}.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long>,
        JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCaseAndIdNot(String login, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByFuncionarioId(Long funcionarioId);

    boolean existsByFuncionarioIdAndIdNot(Long funcionarioId, Long id);

    @Override
    @EntityGraph(attributePaths = "funcionario")
    Optional<Usuario> findById(Long id);
}
