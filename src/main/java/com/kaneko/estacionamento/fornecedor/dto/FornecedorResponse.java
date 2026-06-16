package com.kaneko.estacionamento.fornecedor.dto;

import com.kaneko.estacionamento.fornecedor.Fornecedor;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Representacao de saida de um Fornecedor, com referencias resumidas e as
 * colecoes de contato.
 */
public record FornecedorResponse(
        Long id,
        String nome,
        String nomeFantasia,
        String apelido,
        TipoPessoa tipo,
        String documento,
        String rgInscricaoEstadual,
        String endereco,
        String numero,
        String complemento,
        String bairro,
        String cep,
        Long cidadeId,
        String cidadeNome,
        Long nacionalidadeId,
        String nacionalidadeNome,
        Long condicaoPagamentoId,
        String condicaoPagamentoNome,
        BigDecimal limiteCredito,
        String observacao,
        Boolean ativo,
        List<EmailResponse> emails,
        List<TelefoneResponse> telefones,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static FornecedorResponse from(Fornecedor f) {
        return new FornecedorResponse(
                f.getId(),
                f.getNome(),
                f.getNomeFantasia(),
                f.getApelido(),
                f.getTipo(),
                f.getDocumento(),
                f.getRgInscricaoEstadual(),
                f.getEndereco(),
                f.getNumero(),
                f.getComplemento(),
                f.getBairro(),
                f.getCep(),
                f.getCidade() != null ? f.getCidade().getId() : null,
                f.getCidade() != null ? f.getCidade().getNome() : null,
                f.getNacionalidade() != null ? f.getNacionalidade().getId() : null,
                f.getNacionalidade() != null ? f.getNacionalidade().getNome() : null,
                f.getCondicaoPagamento() != null ? f.getCondicaoPagamento().getId() : null,
                f.getCondicaoPagamento() != null ? f.getCondicaoPagamento().getNome() : null,
                f.getLimiteCredito(),
                f.getObservacao(),
                f.getAtivo(),
                f.getEmails().stream().map(EmailResponse::from).toList(),
                f.getTelefones().stream().map(TelefoneResponse::from).toList(),
                f.getCreatedAt(),
                f.getUpdatedAt());
    }
}
