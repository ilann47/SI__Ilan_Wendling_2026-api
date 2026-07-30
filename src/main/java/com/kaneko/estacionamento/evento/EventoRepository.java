package com.kaneko.estacionamento.evento;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoRepository extends JpaRepository<Evento, Long> {
    Optional<Evento> findByIdAndOrganizacaoId(Long id, Long organizacaoId);
    boolean existsByOrganizacaoIdAndExternalIdIgnoreCase(Long organizacaoId, String externalId);
}
