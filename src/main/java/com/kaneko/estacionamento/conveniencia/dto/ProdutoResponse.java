package com.kaneko.estacionamento.conveniencia.dto;

import com.kaneko.estacionamento.conveniencia.Produto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um Produto, com marca, unidade e categoria
 * resumidas.
 */
public record ProdutoResponse(
        Long id,
        String nome,
        String codigoBarras,
        String referencia,
        Long marcaId,
        String marcaNome,
        Long unidadeMedidaId,
        String unidadeMedidaSigla,
        Long categoriaId,
        String categoriaNome,
        BigDecimal valorCompra,
        BigDecimal custo,
        BigDecimal valorVenda,
        BigDecimal percentualLucro,
        BigDecimal quantidade,
        BigDecimal quantidadeMinima,
        String descricao,
        String observacao,
        Boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ProdutoResponse from(Produto p) {
        return new ProdutoResponse(
                p.getId(),
                p.getNome(),
                p.getCodigoBarras(),
                p.getReferencia(),
                p.getMarca().getId(),
                p.getMarca().getNome(),
                p.getUnidadeMedida().getId(),
                p.getUnidadeMedida().getSigla(),
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getNome() : null,
                p.getValorCompra(),
                p.getCusto(),
                p.getValorVenda(),
                p.getPercentualLucro(),
                p.getQuantidade(),
                p.getQuantidadeMinima(),
                p.getDescricao(),
                p.getObservacao(),
                p.getAtivo(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
