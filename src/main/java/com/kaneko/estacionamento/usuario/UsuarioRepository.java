package com.kaneko.estacionamento.usuario;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Usuario}.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCaseAndIdNot(String login, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByFuncionarioId(Long funcionarioId);

    boolean existsByFuncionarioIdAndIdNot(Long funcionarioId, Long id);

    @Override
    @EntityGraph(attributePaths = "funcionario")
    Page<Usuario> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "funcionario")
    Optional<Usuario> findById(Long id);
}
