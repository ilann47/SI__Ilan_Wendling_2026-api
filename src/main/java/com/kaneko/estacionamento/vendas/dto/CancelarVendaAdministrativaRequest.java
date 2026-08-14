package com.kaneko.estacionamento.vendas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarVendaAdministrativaRequest(
        @NotBlank @Size(max = 300) String motivo) {
}
