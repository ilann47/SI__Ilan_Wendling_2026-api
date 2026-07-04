package com.kaneko.estacionamento.pessoas.dto;

import com.kaneko.estacionamento.pessoas.EstadoCivil;
import com.kaneko.estacionamento.pessoas.Sexo;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de entrada para criacao ou alteracao de um Cliente. Quando o tipo nao
 * e informado, assume-se FISICA; o limite de credito padrao e zero.
 */
public record ClienteRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @Size(max = 60, message = "O apelido deve ter no máximo 60 caracteres.")
        String apelido,

        TipoPessoa tipo,

        @NotBlank(message = "O documento (CPF/CNPJ) é obrigatório.")
        @Size(max = 20, message = "O documento deve ter no máximo 20 caracteres.")
        String documento,

        @Size(max = 20) String rgInscricaoEstadual,
        LocalDate dataNascimento,
        Sexo sexo,
        EstadoCivil estadoCivil,
        @Size(max = 20) String telefone,
        @Email(message = "E-mail inválido.") @Size(max = 120) String email,
        @Size(max = 120) String endereco,
        @Size(max = 10) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 60) String bairro,
        @Size(max = 9) String cep,

        Long cidadeId,
        Long condicaoPagamentoId,

        @PositiveOrZero(message = "O limite de crédito não pode ser negativo.")
        BigDecimal limiteCredito,

        @Size(max = 255) String observacao,

        Boolean ativo
) {
}
