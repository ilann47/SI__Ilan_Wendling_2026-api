package com.kaneko.estacionamento.pessoas.dto;

import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.EstadoCivil;
import com.kaneko.estacionamento.pessoas.Sexo;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Cliente, com cidade e nacionalidade resumidas.
 */
public record ClienteResponse(
        Long id,
        String nome,
        String apelido,
        TipoPessoa tipo,
        String documento,
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
        Long condicaoPagamentoId,
        String condicaoPagamentoNome,
        BigDecimal limiteCredito,
        String observacao,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(
                c.getId(),
                c.getNome(),
                c.getApelido(),
                c.getTipo(),
                c.getDocumento(),
                c.getRgInscricaoEstadual(),
                c.getDataNascimento(),
                c.getSexo(),
                c.getEstadoCivil(),
                c.getTelefone(),
                c.getEmail(),
                c.getEndereco(),
                c.getNumero(),
                c.getComplemento(),
                c.getBairro(),
                c.getCep(),
                c.getCidade() != null ? c.getCidade().getId() : null,
                c.getCidade() != null ? c.getCidade().getNome() : null,
                c.getCondicaoPagamento() != null ? c.getCondicaoPagamento().getId() : null,
                c.getCondicaoPagamento() != null ? c.getCondicaoPagamento().getNome() : null,
                c.getLimiteCredito(),
                c.getObservacao(),
                c.getAtivo(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
