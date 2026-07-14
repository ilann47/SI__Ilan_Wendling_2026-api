package com.kaneko.estacionamento.patio.dto;

import com.kaneko.estacionamento.patio.Patio;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Patio, com a cidade resumida e a ocupacao atual
 * ({@code vagasOcupadas}, derivada das movimentacoes ABERTAS).
 */
public record PatioResponse(
        Long id,
        String nome,
        String endereco,
        String numero,
        String complemento,
        String bairro,
        String cep,
        Long cidadeId,
        String cidadeNome,
        Integer capacidadeVagas,
        long vagasOcupadas,
        String observacao,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static PatioResponse from(Patio p, long vagasOcupadas) {
        return new PatioResponse(
                p.getId(),
                p.getNome(),
                p.getEndereco(),
                p.getNumero(),
                p.getComplemento(),
                p.getBairro(),
                p.getCep(),
                p.getCidade() != null ? p.getCidade().getId() : null,
                p.getCidade() != null ? p.getCidade().getNome() : null,
                p.getCapacidadeVagas(),
                vagasOcupadas,
                p.getObservacao(),
                p.getAtivo(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
