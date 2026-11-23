package com.kaneko.estacionamento.instalacoes.dto;

import com.kaneko.estacionamento.instalacoes.CategoriaSetor;
import com.kaneko.estacionamento.instalacoes.Vaga;

public record ItemImportacaoVagaResponse(
        int inputIndex,
        StatusImportacaoVaga status,
        Long id,
        String code,
        CategoriaSetor type,
        Boolean accessible,
        String position,
        Long version,
        String detail) {

    public static ItemImportacaoVagaResponse sucesso(
            int indice, StatusImportacaoVaga status, Vaga vaga) {
        return new ItemImportacaoVagaResponse(
                indice, status, vaga.getId(), vaga.getCodigo(), vaga.getTipo(),
                vaga.getAcessivel(), vaga.getPosicao(), vaga.getVersion(), null);
    }

    public static ItemImportacaoVagaResponse conflito(
            int indice, CriarVagaItemRequest item, String codigo, String detalhe) {
        return new ItemImportacaoVagaResponse(
                indice, StatusImportacaoVaga.CONFLITO, null, codigo, item.type(),
                item.accessible(), item.position(), null, detalhe);
    }
}
