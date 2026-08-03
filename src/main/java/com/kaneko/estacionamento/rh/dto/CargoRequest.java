package com.kaneko.estacionamento.rh.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dados de entrada para criacao ou alteracao de um Cargo.
 */
public record CargoRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String nome,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
        String descricao,

        @PositiveOrZero(message = "O salário base não pode ser negativo.")
        BigDecimal salarioBase,

        @PositiveOrZero(message = "A carga horária não pode ser negativa.")
        BigDecimal cargaHoraria,

        Boolean requerCnh,

        Boolean ativo
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
