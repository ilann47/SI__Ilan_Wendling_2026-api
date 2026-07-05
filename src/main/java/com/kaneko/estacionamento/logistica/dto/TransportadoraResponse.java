package com.kaneko.estacionamento.logistica.dto;

import com.kaneko.estacionamento.logistica.Transportadora;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma Transportadora, com cidade e condicao de
 * pagamento resumidas.
 */
public record TransportadoraResponse(
        Long id,
        String nome,
        String nomeFantasia,
        TipoPessoa tipo,
        String documento,
        String rgInscricaoEstadual,
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
        String observacao,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static TransportadoraResponse from(Transportadora t) {
        return new TransportadoraResponse(
                t.getId(),
                t.getNome(),
                t.getNomeFantasia(),
                t.getTipo(),
                t.getDocumento(),
                t.getRgInscricaoEstadual(),
                t.getTelefone(),
                t.getEmail(),
                t.getEndereco(),
                t.getNumero(),
                t.getComplemento(),
                t.getBairro(),
                t.getCep(),
                t.getCidade() != null ? t.getCidade().getId() : null,
                t.getCidade() != null ? t.getCidade().getNome() : null,
                t.getCondicaoPagamento() != null ? t.getCondicaoPagamento().getId() : null,
                t.getCondicaoPagamento() != null ? t.getCondicaoPagamento().getNome() : null,
                t.getObservacao(),
                t.getAtivo(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
