package com.kaneko.estacionamento.compras.dto;

import com.kaneko.estacionamento.compras.OrdemCompra;
import com.kaneko.estacionamento.compras.StatusOrdemCompra;
import com.kaneko.estacionamento.compras.TipoFreteOrdemCompra;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record OrdemCompraResponse(
        Long id,
        Long organizacaoId,
        String numero,
        String numeroNota,
        String serieNota,
        String modeloNota,
        Long fornecedorId,
        String fornecedorNome,
        StatusOrdemCompra status,
        LocalDate dataEmissao,
        LocalDate previsaoEntrega,
        Long condicaoPagamentoId,
        String condicaoPagamentoNome,
        Long transportadoraId,
        String transportadoraNome,
        Long localEstoqueEntregaId,
        String localEstoqueEntregaNome,
        Long compradorId,
        String compradorNome,
        TipoFreteOrdemCompra tipoFrete,
        String referenciaFornecedor,
        String moeda,
        BigDecimal subtotal,
        BigDecimal valorFrete,
        BigDecimal valorSeguro,
        BigDecimal outrasDespesas,
        BigDecimal valorDesconto,
        BigDecimal valorTotal,
        String observacao,
        String observacaoInterna,
        String motivoCancelamento,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ItemOrdemCompraResponse> itens) {

    public static OrdemCompraResponse from(OrdemCompra ordem) {
        return new OrdemCompraResponse(ordem.getId(), ordem.getOrganizacaoId(),
                ordem.getNumero(), ordem.getNumeroNota(), ordem.getSerieNota(),
                ordem.getModeloNota(), ordem.getFornecedor().getId(),
                ordem.getFornecedor().getNome(), ordem.getStatus(), ordem.getDataEmissao(),
                ordem.getPrevisaoEntrega(),
                ordem.getCondicaoPagamento() == null ? null : ordem.getCondicaoPagamento().getId(),
                ordem.getCondicaoPagamento() == null ? null : ordem.getCondicaoPagamento().getNome(),
                ordem.getTransportadora() == null ? null : ordem.getTransportadora().getId(),
                ordem.getTransportadora() == null ? null : ordem.getTransportadora().getNome(),
                ordem.getLocalEstoqueEntrega() == null ? null : ordem.getLocalEstoqueEntrega().getId(),
                ordem.getLocalEstoqueEntrega() == null ? null : ordem.getLocalEstoqueEntrega().getNome(),
                ordem.getComprador() == null ? null : ordem.getComprador().getId(),
                ordem.getComprador() == null ? null : ordem.getComprador().getNome(),
                ordem.getTipoFrete(), ordem.getReferenciaFornecedor(),
                ordem.getMoeda(), ordem.getSubtotal(),
                ordem.getValorFrete(), ordem.getValorSeguro(), ordem.getOutrasDespesas(),
                ordem.getValorDesconto(), ordem.getValorTotal(),
                ordem.getObservacao(), ordem.getObservacaoInterna(),
                ordem.getMotivoCancelamento(), ordem.getVersion(),
                ordem.getCreatedAt(), ordem.getUpdatedAt(),
                ordem.getItens().stream().map(ItemOrdemCompraResponse::from).toList());
    }
}
