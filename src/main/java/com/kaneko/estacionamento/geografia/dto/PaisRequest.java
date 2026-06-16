package com.kaneko.estacionamento.geografia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criacao ou alteracao de um Pais.
 *
 * <p>As restricoes refletem o schema: {@code nome} obrigatorio e unico
 * (uq_pais_nome), demais campos opcionais com seus limites de tamanho.
 */
public record PaisRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.")
        String nome,

        @Size(max = 3, message = "A sigla deve ter no máximo 3 caracteres.")
        String sigla,

        @Size(max = 5, message = "O código deve ter no máximo 5 caracteres.")
        String codigo,

        @Size(max = 100, message = "A nacionalidade deve ter no máximo 100 caracteres.")
        String nacionalidade,

        Boolean ativo
) {
}
