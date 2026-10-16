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
        Long organizacaoId,
        Long version,
        String numero,
        String modelo,
        String serie,
        Long fornecedorId,
        String fornecedorNome,
        Long condicaoPagamentoId,
        String condicaoPagamentoNome,
        Long localEstoqueId,
        String localEstoqueNome,
        Long recebimentoCompraId,
        Long ordemCompraId,
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
                nota.getOrganizacaoId(),
                nota.getVersion(),
                nota.getNumero(),
                nota.getModelo(),
                nota.getSerie(),
                nota.getFornecedor().getId(),
                nota.getFornecedor().getNome(),
                nota.getCondicaoPagamento() != null ? nota.getCondicaoPagamento().getId() : null,
                nota.getCondicaoPagamento() != null ? nota.getCondicaoPagamento().getNome() : null,
                nota.getLocalEstoque() != null ? nota.getLocalEstoque().getId() : null,
                nota.getLocalEstoque() != null ? nota.getLocalEstoque().getNome() : null,
                nota.getRecebimentoCompra() != null ? nota.getRecebimentoCompra().getId() : null,
                nota.getRecebimentoCompra() != null
                        ? nota.getRecebimentoCompra().getOrdemCompra().getId() : null,
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
