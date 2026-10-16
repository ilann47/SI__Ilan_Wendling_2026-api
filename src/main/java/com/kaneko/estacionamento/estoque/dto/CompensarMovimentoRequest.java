package com.kaneko.estacionamento.estoque.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompensarMovimentoRequest(
        @NotBlank(message = "O motivo e obrigatorio.")
        @Size(max = 500, message = "O motivo deve ter no maximo 500 caracteres.")
        String motivo
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
