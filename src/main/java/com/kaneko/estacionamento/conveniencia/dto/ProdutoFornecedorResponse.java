package com.kaneko.estacionamento.conveniencia.dto;

import com.kaneko.estacionamento.conveniencia.ProdutoFornecedor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um vinculo produto-fornecedor.
 */
public record ProdutoFornecedorResponse(
        Long id,
        Long produtoId,
        String produtoNome,
        Long fornecedorId,
        String fornecedorNome,
        String codigoProd,
        BigDecimal custo,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ProdutoFornecedorResponse from(ProdutoFornecedor pf) {
        return new ProdutoFornecedorResponse(
                pf.getId(),
                pf.getProduto().getId(),
                pf.getProduto().getNome(),
                pf.getFornecedor().getId(),
                pf.getFornecedor().getNome(),
                pf.getCodigoProd(),
                pf.getCusto(),
                pf.getAtivo(),
                pf.getCreatedAt(),
                pf.getUpdatedAt());
    }
}
