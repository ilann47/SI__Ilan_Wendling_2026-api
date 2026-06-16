package com.kaneko.estacionamento.geografia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criacao ou alteracao de um Estado.
 */
public record EstadoRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.")
        String nome,

        @NotBlank(message = "A UF é obrigatória.")
        @Size(max = 3, message = "A UF deve ter no máximo 3 caracteres.")
        String uf,

        @NotNull(message = "O país é obrigatório.")
        Long paisId,

        Boolean ativo
) {
}
