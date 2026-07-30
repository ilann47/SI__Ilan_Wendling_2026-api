package com.kaneko.estacionamento.common.outbox;

import com.kaneko.estacionamento.common.web.TraceIdFilter;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
    private final EventoOutboxRepository repository;

    public OutboxService(EventoOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publicar(PublicarEventoOutboxCommand command) {
        String correlationId = MDC.get(TraceIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        EventoOutbox evento = repository.saveAndFlush(EventoOutbox.criar(command, correlationId));
        return evento.getEventId();
    }
}
