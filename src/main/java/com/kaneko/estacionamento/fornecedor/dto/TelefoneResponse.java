package com.kaneko.estacionamento.fornecedor.dto;

import com.kaneko.estacionamento.fornecedor.FornecedorTelefone;
import com.kaneko.estacionamento.fornecedor.TipoTelefone;

/**
 * Representacao de saida de um telefone de contato.
 */
public record TelefoneResponse(
        Long id,
        String telefone,
        TipoTelefone tipo,
        Boolean principal,
        Boolean ativo
) {

    public static TelefoneResponse from(FornecedorTelefone t) {
        return new TelefoneResponse(t.getId(), t.getTelefone(), t.getTipo(), t.getPrincipal(), t.getAtivo());
    }
}
