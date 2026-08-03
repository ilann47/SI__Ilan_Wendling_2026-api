package com.kaneko.estacionamento.fornecedor.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dados de entrada para criacao ou alteracao de um Fornecedor, incluindo
 * (opcionalmente) seus e-mails e telefones de contato.
 */
public record FornecedorRequest(

        @NotBlank(message = "O nome (razão social) é obrigatório.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @Size(max = 120) String nomeFantasia,
        @Size(max = 60) String apelido,
        TipoPessoa tipo,

        @NotBlank(message = "O documento (CNPJ/CPF) é obrigatório.")
        @Size(max = 20, message = "O documento deve ter no máximo 20 caracteres.")
        String documento,

        @Size(max = 20) String rgInscricaoEstadual,
        @Size(max = 120) String endereco,
        @Size(max = 10) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 60) String bairro,
        @Size(max = 9) String cep,

        Long cidadeId,
        Long condicaoPagamentoId,
        Long transportadoraId,

        @PositiveOrZero(message = "O limite de crédito não pode ser negativo.")
        BigDecimal limiteCredito,

        @Size(max = 255) String observacao,

        Boolean ativo,

        @Valid List<EmailRequest> emails,
        @Valid List<TelefoneRequest> telefones
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
