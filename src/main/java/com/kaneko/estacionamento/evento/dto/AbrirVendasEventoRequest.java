package com.kaneko.estacionamento.evento.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.OffsetDateTime;

public record AbrirVendasEventoRequest(OffsetDateTime startsAt) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
