package com.kaneko.estacionamento.relatorio.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Relatorio de estoque minimo (4.29): produtos cujo estoque atual esta igual ou
 * abaixo do minimo, para apoiar a reposicao.
 */
public record EstoqueMinimoResponse(int total, List<ItemEstoque> itens) {

    public record ItemEstoque(
            Long produtoId,
            String produto,
            String marca,
            String categoria,
            BigDecimal quantidade,
            BigDecimal quantidadeMinima) {
    }
}
