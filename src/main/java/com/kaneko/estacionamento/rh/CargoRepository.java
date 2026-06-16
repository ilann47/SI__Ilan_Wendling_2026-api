package com.kaneko.estacionamento.rh;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Cargo}.
 */
public interface CargoRepository extends JpaRepository<Cargo, Long> {

    boolean existsByNome(String nome);

    boolean existsByNomeAndIdNot(String nome, Long id);
}
