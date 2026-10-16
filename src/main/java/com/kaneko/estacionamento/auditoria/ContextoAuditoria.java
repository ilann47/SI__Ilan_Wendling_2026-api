package com.kaneko.estacionamento.auditoria;

record ContextoAuditoria(
        TipoAtorAuditoria atorTipo,
        Long atorId,
        OrigemAuditoria origem,
        String dispositivoId,
        String ipOrigem,
        String traceId) {}
