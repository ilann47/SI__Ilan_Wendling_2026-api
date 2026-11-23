package com.kaneko.estacionamento.acesso.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RealizarCheckinRequest(
        @NotBlank
        @Pattern(regexp = "KQ1-[A-Za-z0-9_-]{43}") String qrToken,
        @NotNull Long eventId,
        @NotNull Long parkingFacilityId,
        @NotBlank @Size(max = 80) String lane) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
