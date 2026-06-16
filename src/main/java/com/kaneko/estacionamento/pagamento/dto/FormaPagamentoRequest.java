package com.kaneko.estacionamento.pagamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criacao ou alteracao de uma Forma de Pagamento.
 *
 * <p>As restricoes refletem o schema: {@code nome} obrigatorio e unico
 * (uq_forma_pagamento_nome) e {@code descricao} obrigatoria, ambos com seus
 * limites de tamanho.
 */
public record FormaPagamentoRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String nome,

        @NotBlank(message = "A descrição é obrigatória.")
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
        String descricao,

        Boolean ativo
) {
}
