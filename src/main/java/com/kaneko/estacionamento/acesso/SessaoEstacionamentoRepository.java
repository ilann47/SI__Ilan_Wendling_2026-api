package com.kaneko.estacionamento.acesso;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessaoEstacionamentoRepository
        extends JpaRepository<SessaoEstacionamento, Long> {
    Optional<SessaoEstacionamento> findByTentativaEntradaId(
            Long tentativaEntradaId);
}
