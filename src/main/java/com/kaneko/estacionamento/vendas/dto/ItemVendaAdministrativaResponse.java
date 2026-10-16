package com.kaneko.estacionamento.vendas.dto;

import com.kaneko.estacionamento.vendas.ItemVendaAdministrativa;
import java.math.BigDecimal;

public record ItemVendaAdministrativaResponse(
        Long id, Integer sequencia, Long produtoId, String produtoNome,
        BigDecimal quantidade, BigDecimal valorUnitario,
        BigDecimal valorDesconto, BigDecimal valorTotal) {
    public static ItemVendaAdministrativaResponse from(ItemVendaAdministrativa item) {
        return new ItemVendaAdministrativaResponse(item.getId(), item.getSequencia(),
                item.getProduto().getId(), item.getProduto().getNome(), item.getQuantidade(),
                item.getValorUnitario(), item.getValorDesconto(), item.getValorTotal());
    }
}
