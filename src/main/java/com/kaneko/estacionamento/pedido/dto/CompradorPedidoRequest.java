package com.kaneko.estacionamento.pedido.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompradorPedidoRequest(
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Pattern(regexp = "(?:[0-9.\\/\\-]*[0-9])?")
        @Size(max = 18) String document) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo 'buyer." + campo + "' nao e aceito neste contrato.");
    }
}
