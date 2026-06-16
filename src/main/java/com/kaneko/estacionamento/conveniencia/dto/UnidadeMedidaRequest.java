package com.kaneko.estacionamento.conveniencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criacao ou alteracao de uma UnidadeMedida.
 *
 * <p>As restricoes refletem o schema: {@code nome} e {@code sigla}
 * obrigatorios, sendo a {@code sigla} unica (uq_unidade_sigla); {@code ativo}
 * e opcional.
 */
public record UnidadeMedidaRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String nome,

        @NotBlank(message = "A sigla é obrigatória.")
        @Size(max = 6, message = "A sigla deve ter no máximo 6 caracteres.")
        String sigla,

        Boolean ativo
) {
}
