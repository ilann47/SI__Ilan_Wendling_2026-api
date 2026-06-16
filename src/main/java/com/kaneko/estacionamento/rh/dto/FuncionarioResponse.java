package com.kaneko.estacionamento.rh.dto;

import com.kaneko.estacionamento.pessoas.EstadoCivil;
import com.kaneko.estacionamento.pessoas.Sexo;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import com.kaneko.estacionamento.rh.Funcionario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Funcionario, com cargo, cidade e nacionalidade
 * resumidos.
 */
public record FuncionarioResponse(
        Long id,
        String nome,
        String apelido,
        TipoPessoa tipo,
        String cpf,
        String rgInscricaoEstadual,
        LocalDate dataNascimento,
        Sexo sexo,
        EstadoCivil estadoCivil,
        String telefone,
        String email,
        String endereco,
        String numero,
        String complemento,
        String bairro,
        String cep,
        Long cidadeId,
        String cidadeNome,
        Long nacionalidadeId,
        String nacionalidadeNome,
        Long cargoId,
        String cargoNome,
        String cnh,
        LocalDate dataValidadeCnh,
        LocalDate dataAdmissao,
        LocalDate dataDemissao,
        BigDecimal salario,
        String observacao,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static FuncionarioResponse from(Funcionario f) {
        return new FuncionarioResponse(
                f.getId(),
                f.getNome(),
                f.getApelido(),
                f.getTipo(),
                f.getCpf(),
                f.getRgInscricaoEstadual(),
                f.getDataNascimento(),
                f.getSexo(),
                f.getEstadoCivil(),
                f.getTelefone(),
                f.getEmail(),
                f.getEndereco(),
                f.getNumero(),
                f.getComplemento(),
                f.getBairro(),
                f.getCep(),
                f.getCidade() != null ? f.getCidade().getId() : null,
                f.getCidade() != null ? f.getCidade().getNome() : null,
                f.getNacionalidade() != null ? f.getNacionalidade().getId() : null,
                f.getNacionalidade() != null ? f.getNacionalidade().getNome() : null,
                f.getCargo().getId(),
                f.getCargo().getNome(),
                f.getCnh(),
                f.getDataValidadeCnh(),
                f.getDataAdmissao(),
                f.getDataDemissao(),
                f.getSalario(),
                f.getObservacao(),
                f.getAtivo(),
                f.getCreatedAt(),
                f.getUpdatedAt());
    }
}
