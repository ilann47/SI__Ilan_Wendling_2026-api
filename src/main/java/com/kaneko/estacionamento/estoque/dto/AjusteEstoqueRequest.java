package com.kaneko.estacionamento.estoque.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AjusteEstoqueRequest(
        @NotNull(message = "O produto e obrigatorio.") Long produtoId,
        @NotNull(message = "O local de estoque e obrigatorio.") Long localEstoqueId,
        @NotNull(message = "O delta e obrigatorio.")
        @Digits(integer = 12, fraction = 3, message = "O delta deve ter no maximo 3 casas decimais.")
        BigDecimal delta,
        @PositiveOrZero(message = "O custo unitario nao pode ser negativo.")
        @Digits(integer = 13, fraction = 2, message = "O custo deve ter no maximo 2 casas decimais.")
        BigDecimal custoUnitario,
        @NotBlank(message = "O motivo e obrigatorio.")
        @Size(max = 500, message = "O motivo deve ter no maximo 500 caracteres.")
        String motivo
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
