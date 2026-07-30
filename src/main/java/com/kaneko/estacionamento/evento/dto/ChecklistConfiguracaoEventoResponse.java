package com.kaneko.estacionamento.evento.dto;

public record ChecklistConfiguracaoEventoResponse(
        boolean ready,
        boolean venue,
        boolean parkingAllocation,
        boolean parkingProduct,
        boolean responsibleParty,
        boolean policies) {

    public static ChecklistConfiguracaoEventoResponse inicial() {
        return new ChecklistConfiguracaoEventoResponse(false, true, false, false, false, false);
    }
}
