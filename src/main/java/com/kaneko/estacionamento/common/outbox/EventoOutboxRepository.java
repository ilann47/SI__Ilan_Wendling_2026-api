package com.kaneko.estacionamento.common.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, UUID> {}
