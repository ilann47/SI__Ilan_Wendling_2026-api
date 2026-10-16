package com.kaneko.estacionamento.fornecedor.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.fornecedor.TipoTelefone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada de um telefone de contato do fornecedor.
 */
public record TelefoneRequest(

        @NotBlank(message = "O telefone é obrigatório.")
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres.")
        String telefone,

        TipoTelefone tipo,

        Boolean principal
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito no contato de telefone.");
    }
}
