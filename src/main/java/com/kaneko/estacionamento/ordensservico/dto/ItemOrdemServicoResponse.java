package com.kaneko.estacionamento.ordensservico.dto;

import com.kaneko.estacionamento.ordensservico.ItemOrdemServico;
import java.math.BigDecimal;

public record ItemOrdemServicoResponse(Long id, Integer sequencia, Long servicoId,
        String servico, String descricao, BigDecimal quantidade,
        BigDecimal valorUnitario, BigDecimal valorDesconto, BigDecimal valorTotal) {
    public static ItemOrdemServicoResponse from(ItemOrdemServico item) {
        return new ItemOrdemServicoResponse(item.getId(), item.getSequencia(),
                item.getServico().getId(), item.getServico().getNome(),
                item.getDescricaoSnapshot(), item.getQuantidade(), item.getValorUnitario(),
                item.getValorDesconto(), item.getValorTotal());
    }
}
