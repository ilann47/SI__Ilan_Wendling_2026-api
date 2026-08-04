package com.kaneko.estacionamento.financeiro.dto;

import com.kaneko.estacionamento.financeiro.ContaPagar;
import com.kaneko.estacionamento.financeiro.SituacaoContaPagar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um titulo a pagar.
 */
public record ContaPagarResponse(
        Long id,
        Long organizacaoId,
        Long version,
        Long fornecedorId,
        String fornecedorNome,
        Long notaEntradaId,
        String notaEntradaNumero,
        Long condicaoPagamentoId,
        Long formaPagamentoId,
        String formaPagamentoNome,
        Integer numeroParcela,
        Integer totalParcelas,
        BigDecimal valorOriginal,
        BigDecimal valorPago,
        BigDecimal valorDesconto,
        BigDecimal valorJuros,
        BigDecimal valorMulta,
        BigDecimal valorTotal,
        LocalDate dataEmissao,
        LocalDate dataVencimento,
        LocalDate dataPagamento,
        SituacaoContaPagar situacao,
        String observacao,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ContaPagarResponse from(ContaPagar c) {
        return new ContaPagarResponse(
                c.getId(),
                c.getOrganizacaoId(),
                c.getVersion(),
                c.getFornecedor().getId(),
                c.getFornecedor().getNome(),
                c.getNotaEntrada() != null ? c.getNotaEntrada().getId() : null,
                c.getNotaEntrada() != null ? c.getNotaEntrada().getNumero() : null,
                c.getCondicaoPagamento() != null ? c.getCondicaoPagamento().getId() : null,
                c.getFormaPagamento() != null ? c.getFormaPagamento().getId() : null,
                c.getFormaPagamento() != null ? c.getFormaPagamento().getNome() : null,
                c.getNumeroParcela(),
                c.getTotalParcelas(),
                c.getValorOriginal(),
                c.getValorPago(),
                c.getValorDesconto(),
                c.getValorJuros(),
                c.getValorMulta(),
                c.getValorTotal(),
                c.getDataEmissao(),
                c.getDataVencimento(),
                c.getDataPagamento(),
                c.getSituacao(),
                c.getObservacao(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
