package com.kaneko.estacionamento.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevogarPapelRequest(@NotBlank @Size(max = 300) String reason) {}
