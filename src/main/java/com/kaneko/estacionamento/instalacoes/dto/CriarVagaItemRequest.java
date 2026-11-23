package com.kaneko.estacionamento.instalacoes.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.instalacoes.CategoriaSetor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarVagaItemRequest(
        @NotBlank @Size(max = 40) String code,
        @NotNull CategoriaSetor type,
        @NotNull Boolean accessible,
        @Size(max = 120) String position) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste item.");
    }
}
