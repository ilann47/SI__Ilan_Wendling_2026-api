package com.kaneko.estacionamento.instalacoes.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.instalacoes.CategoriaSetor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CriarSetorRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(1) Integer capacity,
        @NotEmpty Set<@NotNull CategoriaSetor> categories) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
