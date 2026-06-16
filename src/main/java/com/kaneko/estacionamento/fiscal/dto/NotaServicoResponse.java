package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.fiscal.NotaServico;
import com.kaneko.estacionamento.fiscal.SituacaoNotaServico;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Representacao de saida de uma nota de servico.
 */
public record NotaServicoResponse(
        Long id,
        String numero,
        String modelo,
        String serie,
        Long clienteId,
        String clienteNome,
        Long servicoId,
        Long movimentacaoId,
        Long mensalistaId,
        Long condicaoPagamentoId,
        Long formaPagamentoId,
        LocalDate dataEmissao,
        BigDecimal valorServico,
        BigDecimal aliquotaIss,
        BigDecimal valorIss,
        BigDecimal valorDesconto,
        BigDecimal valorTotal,
        SituacaoNotaServico situacao,
        String observacao,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static NotaServicoResponse from(NotaServico n) {
        return new NotaServicoResponse(
                n.getId(),
                n.getNumero(),
                n.getModelo(),
                n.getSerie(),
                n.getCliente().getId(),
                n.getCliente().getNome(),
                n.getServico() != null ? n.getServico().getId() : null,
                n.getMovimentacao() != null ? n.getMovimentacao().getId() : null,
                n.getMensalista() != null ? n.getMensalista().getId() : null,
                n.getCondicaoPagamento() != null ? n.getCondicaoPagamento().getId() : null,
                n.getFormaPagamento() != null ? n.getFormaPagamento().getId() : null,
                n.getDataEmissao(),
                n.getValorServico(),
                n.getAliquotaIss(),
                n.getValorIss(),
                n.getValorDesconto(),
                n.getValorTotal(),
                n.getSituacao(),
                n.getObservacao(),
                n.getCreatedAt(),
                n.getUpdatedAt());
    }
}
