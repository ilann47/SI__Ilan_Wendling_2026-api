package com.kaneko.estacionamento.estoque.dto;

import com.kaneko.estacionamento.estoque.SaldoEstoqueView;
import java.math.BigDecimal;

public record SaldoEstoqueResponse(
        Long id,
        Long produtoId,
        String produto,
        Long localEstoqueId,
        String localEstoque,
        BigDecimal quantidade,
        Long version
) {
    public static SaldoEstoqueResponse from(SaldoEstoqueView view) {
        return new SaldoEstoqueResponse(view.getId(), view.getProdutoId(), view.getProduto(),
                view.getLocalEstoqueId(), view.getLocalEstoque(), view.getQuantidade(),
                view.getVersion());
    }
}
