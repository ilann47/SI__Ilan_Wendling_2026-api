package com.kaneko.estacionamento.rh.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.pessoas.EstadoCivil;
import com.kaneko.estacionamento.pessoas.Sexo;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de entrada para criacao ou alteracao de um Funcionario. O cargo e
 * obrigatorio; quando o tipo nao e informado, assume FISICA; quando a data de
 * admissao e omitida, assume a data atual.
 */
public record FuncionarioRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @Size(max = 60) String apelido,
        TipoPessoa tipo,

        @NotBlank(message = "O CPF é obrigatório.")
        @Size(max = 20, message = "O CPF deve ter no máximo 20 caracteres.")
        String cpf,

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

        @NotNull(message = "O cargo é obrigatório.")
        Long cargoId,

        @Size(max = 20) String cnh,
        LocalDate dataValidadeCnh,
        LocalDate dataAdmissao,
        LocalDate dataDemissao,

        @PositiveOrZero(message = "O salário não pode ser negativo.")
        BigDecimal salario,

        @Size(max = 255) String observacao,

        Boolean ativo
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException(
                "O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
