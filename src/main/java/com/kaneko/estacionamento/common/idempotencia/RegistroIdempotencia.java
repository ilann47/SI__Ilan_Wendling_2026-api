package com.kaneko.estacionamento.common.idempotencia;

public record RegistroIdempotencia(String requestHash, String recursoTipo, Long recursoId) {}
