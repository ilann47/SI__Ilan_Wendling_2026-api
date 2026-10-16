package com.kaneko.estacionamento.compras.dto;

import com.kaneko.estacionamento.compras.ItemOrdemCompra;
import java.math.BigDecimal;

public record ItemOrdemCompraResponse(
        Long id,
        Integer sequencia,
        Long produtoId,
        String produtoNome,
        BigDecimal quantidadePedida,
        BigDecimal quantidadeRecebida,
        BigDecimal quantidadePendente,
        BigDecimal valorUnitario,
        String codigoProdutoFornecedor,
        BigDecimal valorDesconto,
        BigDecimal valorTotal,
        BigDecimal rateioFrete,
        BigDecimal rateioSeguro,
        BigDecimal rateioOutrasDespesas,
        BigDecimal custoTotal,
        BigDecimal custoUnitarioFinal) {

    public static ItemOrdemCompraResponse from(ItemOrdemCompra item) {
        return new ItemOrdemCompraResponse(item.getId(), item.getSequencia(),
                item.getProduto().getId(), item.getProduto().getNome(),
                item.getQuantidadePedida(), item.getQuantidadeRecebida(),
                item.saldoReceber(), item.getValorUnitario(), item.getCodigoProdutoFornecedor(),
                item.getValorDesconto(),
                item.getValorTotal(), item.getRateioFrete(), item.getRateioSeguro(),
                item.getRateioOutrasDespesas(), item.getCustoTotal(),
                item.getCustoUnitarioFinal());
    }
}
