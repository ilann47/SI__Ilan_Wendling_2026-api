package com.kaneko.estacionamento.estoque.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocalEstoqueRequest(
        @NotBlank(message = "O nome e obrigatorio.")
        @Size(max = 120, message = "O nome deve ter no maximo 120 caracteres.")
        String nome,
        Boolean ativo
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
