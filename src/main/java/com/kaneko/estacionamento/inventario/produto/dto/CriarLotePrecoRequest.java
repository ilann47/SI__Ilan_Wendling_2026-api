package com.kaneko.estacionamento.inventario.produto.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CriarLotePrecoRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull OffsetDateTime salesStartsAt,
        @NotNull OffsetDateTime salesEndsAt,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Min(0) Integer priority) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
