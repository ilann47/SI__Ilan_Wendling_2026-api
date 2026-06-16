package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.fiscal.NotaEntrada;
import com.kaneko.estacionamento.fiscal.SituacaoNota;
import com.kaneko.estacionamento.fiscal.TipoFrete;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Representacao de saida de uma nota fiscal de entrada, com os itens.
 */
public record NotaEntradaResponse(
        Long id,
        String numero,
        String modelo,
        String serie,
        Long fornecedorId,
        String fornecedorNome,
        Long condicaoPagamentoId,
        String condicaoPagamentoNome,
        LocalDate dataEmissao,
        LocalDate dataChegada,
        TipoFrete tipoFrete,
        BigDecimal valorProdutos,
        BigDecimal valorFrete,
        BigDecimal valorSeguro,
        BigDecimal outrasDespesas,
        BigDecimal valorDesconto,
        BigDecimal valorTotal,
        SituacaoNota situacao,
        String observacao,
        List<ItemNotaEntradaResponse> itens,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static NotaEntradaResponse from(NotaEntrada nota) {
        return new NotaEntradaResponse(
                nota.getId(),
                nota.getNumero(),
                nota.getModelo(),
                nota.getSerie(),
                nota.getFornecedor().getId(),
                nota.getFornecedor().getNome(),
                nota.getCondicaoPagamento() != null ? nota.getCondicaoPagamento().getId() : null,
                nota.getCondicaoPagamento() != null ? nota.getCondicaoPagamento().getNome() : null,
                nota.getDataEmissao(),
                nota.getDataChegada(),
                nota.getTipoFrete(),
                nota.getValorProdutos(),
                nota.getValorFrete(),
                nota.getValorSeguro(),
                nota.getOutrasDespesas(),
                nota.getValorDesconto(),
                nota.getValorTotal(),
                nota.getSituacao(),
                nota.getObservacao(),
                nota.getItens().stream().map(ItemNotaEntradaResponse::from).toList(),
                nota.getCreatedAt(),
                nota.getUpdatedAt());
    }
}
