package com.kaneko.estacionamento.compras.dto;

import com.kaneko.estacionamento.compras.RecebimentoCompra;
import java.time.OffsetDateTime;
import java.util.List;

public record RecebimentoCompraResponse(
        Long id,
        Long ordemCompraId,
        Long localEstoqueId,
        String localEstoqueNome,
        Long atorId,
        String atorNome,
        OffsetDateTime recebidoEm,
        String observacao,
        List<ItemRecebimentoCompraResponse> itens) {

    public static RecebimentoCompraResponse from(RecebimentoCompra recebimento) {
        return new RecebimentoCompraResponse(recebimento.getId(),
                recebimento.getOrdemCompra().getId(), recebimento.getLocalEstoque().getId(),
                recebimento.getLocalEstoque().getNome(), recebimento.getAtor().getId(),
                recebimento.getAtor().getNome(), recebimento.getRecebidoEm(),
                recebimento.getObservacao(), recebimento.getItens().stream()
                        .map(ItemRecebimentoCompraResponse::from).toList());
    }
}
