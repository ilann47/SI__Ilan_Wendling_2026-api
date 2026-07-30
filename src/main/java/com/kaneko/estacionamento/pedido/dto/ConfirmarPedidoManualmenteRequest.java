package com.kaneko.estacionamento.pedido.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmarPedidoManualmenteRequest(
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,29}") String paymentMethod,
        @NotBlank @Size(max = 200) String evidenceReference,
        @NotBlank @Size(max = 300) String reason) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
