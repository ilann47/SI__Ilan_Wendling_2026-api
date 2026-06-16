package com.kaneko.estacionamento.financeiro.dto;

import com.kaneko.estacionamento.financeiro.ContaPagarAvulsa;
import com.kaneko.estacionamento.financeiro.SituacaoContaPagar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma despesa avulsa a pagar.
 */
public record ContaPagarAvulsaResponse(
        Long id,
        String descricao,
        Long fornecedorId,
        String fornecedorNome,
        Long formaPagamentoId,
        String formaPagamentoNome,
        String numeroNota,
        String modelo,
        String serie,
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

    public static ContaPagarAvulsaResponse from(ContaPagarAvulsa c) {
        return new ContaPagarAvulsaResponse(
                c.getId(),
                c.getDescricao(),
                c.getFornecedor() != null ? c.getFornecedor().getId() : null,
                c.getFornecedor() != null ? c.getFornecedor().getNome() : null,
                c.getFormaPagamento() != null ? c.getFormaPagamento().getId() : null,
                c.getFormaPagamento() != null ? c.getFormaPagamento().getNome() : null,
                c.getNumeroNota(),
                c.getModelo(),
                c.getSerie(),
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
