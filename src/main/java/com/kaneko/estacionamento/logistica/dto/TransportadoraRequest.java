package com.kaneko.estacionamento.logistica.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criacao ou alteracao de uma Transportadora. Quando o
 * tipo nao e informado, assume-se JURIDICA.
 */
public record TransportadoraRequest(

        @NotBlank(message = "O nome (razão social) é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String nome,

        @Size(max = 120) String nomeFantasia,
        TipoPessoa tipo,

        @NotBlank(message = "O documento (CNPJ/CPF) é obrigatório.")
        @Size(max = 20, message = "O documento deve ter no máximo 20 caracteres.")
        String documento,

        @Size(max = 20) String rgInscricaoEstadual,
        @Size(max = 20) String telefone,
        @Email(message = "E-mail inválido.") @Size(max = 120) String email,
        @Size(max = 120) String endereco,
        @Size(max = 10) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 60) String bairro,
        @Size(max = 9) String cep,

        Long cidadeId,
        Long condicaoPagamentoId,

        @Size(max = 255) String observacao,

        Boolean ativo
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
