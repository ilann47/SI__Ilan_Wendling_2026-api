package com.kaneko.estacionamento.compras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarOrdemCompraRequest(
        @NotBlank @Size(max = 300) String motivo) {
}
