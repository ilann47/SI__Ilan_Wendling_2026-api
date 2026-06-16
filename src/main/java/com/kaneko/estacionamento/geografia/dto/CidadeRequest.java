package com.kaneko.estacionamento.geografia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criacao ou alteracao de uma Cidade.
 */
public record CidadeRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres.")
        String nome,

        @Size(max = 4, message = "O DDD deve ter no máximo 4 caracteres.")
        String ddd,

        @Size(max = 10, message = "O código IBGE deve ter no máximo 10 caracteres.")
        String codigoIbge,

        @NotNull(message = "O estado é obrigatório.")
        Long estadoId,

        Boolean ativo
) {
}
