package com.kaneko.estacionamento.auditoria;

import java.util.Map;

public record RegistrarAuditoriaCommand(
        Long organizacaoId,
        String acao,
        String recursoTipo,
        Long recursoId,
        Long eventoId,
        Long patioId,
        String motivo,
        Map<String, Object> estadoAnterior,
        Map<String, Object> estadoPosterior,
        Long versaoAnterior,
        Long versaoPosterior,
        ClasseRetencaoAuditoria classeRetencao) {}
