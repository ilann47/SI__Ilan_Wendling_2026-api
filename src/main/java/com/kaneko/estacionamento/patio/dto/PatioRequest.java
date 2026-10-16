package com.kaneko.estacionamento.patio.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criacao ou alteracao de um Patio.
 */
public record PatioRequest(

        @NotBlank(message = "O nome do pátio é obrigatório.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @Size(max = 120) String endereco,
        @Size(max = 10) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 60) String bairro,
        @Size(max = 9) String cep,

        Long cidadeId,

        @NotNull(message = "A capacidade de vagas é obrigatória.")
        @Min(value = 1, message = "A capacidade deve ser de pelo menos 1 vaga.")
        Integer capacidadeVagas,

        @Size(max = 255) String observacao,

        Boolean ativo
) {
}
