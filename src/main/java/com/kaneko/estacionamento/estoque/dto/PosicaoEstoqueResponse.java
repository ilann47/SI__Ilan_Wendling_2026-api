package com.kaneko.estacionamento.estoque.dto;

import com.kaneko.estacionamento.estoque.PosicaoEstoqueView;
import java.math.BigDecimal;

public record PosicaoEstoqueResponse(
        Long produtoId,
        String produto,
        BigDecimal quantidade,
        BigDecimal quantidadeMinima,
        boolean abaixoMinimo
) {
    public static PosicaoEstoqueResponse from(PosicaoEstoqueView view) {
        return new PosicaoEstoqueResponse(view.getProdutoId(), view.getProduto(),
                view.getQuantidade(), view.getQuantidadeMinima(), view.getAbaixoMinimo());
    }
}
