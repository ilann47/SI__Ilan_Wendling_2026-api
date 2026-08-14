package com.kaneko.estacionamento.ordensservico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarOrdemServicoRequest(@NotBlank @Size(max = 300) String motivo) {
}
