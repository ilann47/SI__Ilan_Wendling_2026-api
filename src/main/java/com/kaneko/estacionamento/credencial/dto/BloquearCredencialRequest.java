package com.kaneko.estacionamento.credencial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BloquearCredencialRequest(
        @NotBlank @Size(max = 300) String reason) {
}
