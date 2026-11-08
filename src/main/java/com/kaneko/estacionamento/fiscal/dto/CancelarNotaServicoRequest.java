package com.kaneko.estacionamento.fiscal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarNotaServicoRequest(
        @NotBlank(message = "Informe o motivo do cancelamento.")
        @Size(max = 500) String motivo) {
}
