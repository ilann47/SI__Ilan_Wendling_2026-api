package com.kaneko.estacionamento.financeiro.dto;

import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de um titulo a receber.
 */
public record ContaReceberResponse(
        Long id,
        Long organizacaoId,
        Long version,
        Long clienteId,
        String clienteNome,
        Long notaSaidaId,
        String notaSaidaNumero,
        Long mensalistaId,
        Long condicaoPagamentoId,
        Long formaPagamentoId,
        String formaPagamentoNome,
        Integer numeroParcela,
        Integer totalParcelas,
        BigDecimal valorOriginal,
        BigDecimal valorRecebido,
        BigDecimal valorDesconto,
        BigDecimal valorJuros,
        BigDecimal valorMulta,
        BigDecimal valorTotal,
        LocalDate dataEmissao,
        LocalDate dataVencimento,
        LocalDate dataRecebimento,
        SituacaoContaReceber situacao,
        String observacao,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ContaReceberResponse from(ContaReceber c) {
        return new ContaReceberResponse(
                c.getId(),
                c.getOrganizacaoId(),
                c.getVersion(),
                c.getCliente().getId(),
                c.getCliente().getNome(),
                c.getNotaSaida() != null ? c.getNotaSaida().getId() : null,
                c.getNotaSaida() != null ? c.getNotaSaida().getNumero() : null,
                c.getMensalista() != null ? c.getMensalista().getId() : null,
                c.getCondicaoPagamento() != null ? c.getCondicaoPagamento().getId() : null,
                c.getFormaPagamento() != null ? c.getFormaPagamento().getId() : null,
                c.getFormaPagamento() != null ? c.getFormaPagamento().getNome() : null,
                c.getNumeroParcela(),
                c.getTotalParcelas(),
                c.getValorOriginal(),
                c.getValorRecebido(),
                c.getValorDesconto(),
                c.getValorJuros(),
                c.getValorMulta(),
                c.getValorTotal(),
                c.getDataEmissao(),
                c.getDataVencimento(),
                c.getDataRecebimento(),
                c.getSituacao(),
                c.getObservacao(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
