package com.kaneko.estacionamento.instalacoes.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.instalacoes.StatusVaga;
import jakarta.validation.constraints.NotNull;

public record AlterarSituacaoVagaRequest(@NotNull StatusVaga status) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' não pode ser alterado nesta operação.");
    }
}
