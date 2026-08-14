package com.kaneko.estacionamento.compras.dto;

import com.kaneko.estacionamento.compras.OrdemCompra;
import com.kaneko.estacionamento.compras.StatusOrdemCompra;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record OrdemCompraResponse(
        Long id,
        Long organizacaoId,
        String numero,
        Long fornecedorId,
        String fornecedorNome,
        StatusOrdemCompra status,
        LocalDate dataEmissao,
        LocalDate previsaoEntrega,
        String moeda,
        BigDecimal subtotal,
        BigDecimal valorFrete,
        BigDecimal valorDesconto,
        BigDecimal valorTotal,
        String observacao,
        String motivoCancelamento,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ItemOrdemCompraResponse> itens) {

    public static OrdemCompraResponse from(OrdemCompra ordem) {
        return new OrdemCompraResponse(ordem.getId(), ordem.getOrganizacaoId(),
                ordem.getNumero(), ordem.getFornecedor().getId(),
                ordem.getFornecedor().getNome(), ordem.getStatus(), ordem.getDataEmissao(),
                ordem.getPrevisaoEntrega(), ordem.getMoeda(), ordem.getSubtotal(),
                ordem.getValorFrete(), ordem.getValorDesconto(), ordem.getValorTotal(),
                ordem.getObservacao(), ordem.getMotivoCancelamento(), ordem.getVersion(),
                ordem.getCreatedAt(), ordem.getUpdatedAt(),
                ordem.getItens().stream().map(ItemOrdemCompraResponse::from).toList());
    }
}
