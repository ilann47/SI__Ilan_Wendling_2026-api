package com.kaneko.estacionamento.common.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciais de login.
 */
public record LoginRequest(

        @NotBlank(message = "O login é obrigatório.")
        String login,

        @NotBlank(message = "A senha é obrigatória.")
        String senha
) {
}
