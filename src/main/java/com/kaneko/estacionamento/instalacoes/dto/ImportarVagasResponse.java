package com.kaneko.estacionamento.instalacoes.dto;

import java.util.List;

public record ImportarVagasResponse(
        Long sectorId,
        int requested,
        int created,
        int existing,
        int conflicts,
        List<ItemImportacaoVagaResponse> items) {

    public boolean possuiResultadoParcial() {
        return existing > 0 || conflicts > 0;
    }
}
