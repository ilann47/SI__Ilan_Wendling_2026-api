package com.kaneko.estacionamento.rh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio de {@link Cargo}.
 */
public interface CargoRepository extends JpaRepository<Cargo, Long>,
        JpaSpecificationExecutor<Cargo> {

    boolean existsByNome(String nome);

    boolean existsByNomeAndIdNot(String nome, Long id);
}
