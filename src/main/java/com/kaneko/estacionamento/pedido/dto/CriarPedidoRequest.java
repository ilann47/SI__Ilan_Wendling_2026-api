package com.kaneko.estacionamento.pedido.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriarPedidoRequest(
        @NotNull Long inventoryHoldId,
        @NotNull @Valid CompradorPedidoRequest buyer,
        @Pattern(regexp = "(?:[A-Za-z0-9.-]*[A-Za-z0-9])?")
        @Size(max = 10) String vehiclePlate,
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,29}") String channel) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
