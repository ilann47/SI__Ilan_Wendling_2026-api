package com.kaneko.estacionamento.acesso;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresencaRepository extends JpaRepository<Presenca, Long> {
    Optional<Presenca> findByTentativaCheckinId(Long tentativaCheckinId);
}
