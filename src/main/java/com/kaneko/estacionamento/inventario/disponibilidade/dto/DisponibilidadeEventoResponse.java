package com.kaneko.estacionamento.inventario.disponibilidade.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DisponibilidadeEventoResponse(
        Long eventId,
        OffsetDateTime asOf,
        long totalAvailable,
        boolean guaranteesHold,
        List<ItemDisponibilidadeResponse> items) {}
