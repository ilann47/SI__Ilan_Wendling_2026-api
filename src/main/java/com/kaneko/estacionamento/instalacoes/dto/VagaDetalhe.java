package com.kaneko.estacionamento.instalacoes.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.kaneko.estacionamento.instalacoes.CategoriaSetor;
import java.util.List;

public record VagaDetalhe(@JsonUnwrapped VagaResumo vaga, List<CategoriaSetor> allowedCategories) {
    public Long version() {
        return vaga.version();
    }
}
