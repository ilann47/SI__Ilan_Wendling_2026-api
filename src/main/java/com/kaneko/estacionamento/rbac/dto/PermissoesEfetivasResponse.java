package com.kaneko.estacionamento.rbac.dto;

import java.util.List;

public record PermissoesEfetivasResponse(Long organizationId, List<String> permissions) {}
