package com.kaneko.estacionamento.conveniencia.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criacao ou alteracao de uma Marca.
 *
 * <p>As restricoes refletem o schema: {@code nome} obrigatorio e unico
 * (uq_marca_nome); {@code ativo} opcional.
 */
public record MarcaRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String nome,

        Boolean ativo
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
