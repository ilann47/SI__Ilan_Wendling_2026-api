package com.kaneko.estacionamento.common.outbox;

import java.util.Map;
import java.util.UUID;

public record PublicarEventoOutboxCommand(
        String eventType,
        Long organizacaoId,
        String aggregateType,
        Long aggregateId,
        Long aggregateVersion,
        UUID causationId,
        int schemaVersion,
        Map<String, Object> payload) {}
