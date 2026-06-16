package com.kaneko.estacionamento.fornecedor.dto;

import com.kaneko.estacionamento.fornecedor.FornecedorEmail;
import com.kaneko.estacionamento.fornecedor.TipoEmail;

/**
 * Representacao de saida de um e-mail de contato.
 */
public record EmailResponse(
        Long id,
        String email,
        TipoEmail tipo,
        Boolean principal,
        Boolean ativo
) {

    public static EmailResponse from(FornecedorEmail e) {
        return new EmailResponse(e.getId(), e.getEmail(), e.getTipo(), e.getPrincipal(), e.getAtivo());
    }
}
