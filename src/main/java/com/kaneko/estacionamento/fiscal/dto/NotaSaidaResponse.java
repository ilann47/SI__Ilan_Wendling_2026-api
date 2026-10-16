package com.kaneko.estacionamento.fiscal.dto;

import com.kaneko.estacionamento.fiscal.NotaSaida;
import com.kaneko.estacionamento.fiscal.SituacaoNota;
import com.kaneko.estacionamento.fiscal.TipoFrete;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Representacao de saida de uma nota fiscal de saida, com os itens.
 */
public record NotaSaidaResponse(
        Long id,
        Long organizacaoId,
        Long version,
        String numero,
        String modelo,
        String serie,
        Long clienteId,
        String clienteNome,
        Long condicaoPagamentoId,
        String condicaoPagamentoNome,
        Long localEstoqueId,
        String localEstoqueNome,
        Long vendaAdministrativaId,
        LocalDate dataEmissao,
        LocalDate dataSaida,
        TipoFrete tipoFrete,
        BigDecimal valorProdutos,
        BigDecimal valorFrete,
        BigDecimal valorSeguro,
        BigDecimal outrasDespesas,
        BigDecimal valorDesconto,
        BigDecimal valorTotal,
        SituacaoNota situacao,
        String observacao,
        List<ItemNotaSaidaResponse> itens,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static NotaSaidaResponse from(NotaSaida nota) {
        return new NotaSaidaResponse(
                nota.getId(),
                nota.getOrganizacaoId(),
                nota.getVersion(),
                nota.getNumero(),
                nota.getModelo(),
                nota.getSerie(),
                nota.getCliente().getId(),
                nota.getCliente().getNome(),
                nota.getCondicaoPagamento() != null ? nota.getCondicaoPagamento().getId() : null,
                nota.getCondicaoPagamento() != null ? nota.getCondicaoPagamento().getNome() : null,
                nota.getLocalEstoque() != null ? nota.getLocalEstoque().getId() : null,
                nota.getLocalEstoque() != null ? nota.getLocalEstoque().getNome() : null,
                nota.getVendaAdministrativa() != null
                        ? nota.getVendaAdministrativa().getId() : null,
                nota.getDataEmissao(),
                nota.getDataSaida(),
                nota.getTipoFrete(),
                nota.getValorProdutos(),
                nota.getValorFrete(),
                nota.getValorSeguro(),
                nota.getOutrasDespesas(),
                nota.getValorDesconto(),
                nota.getValorTotal(),
                nota.getSituacao(),
                nota.getObservacao(),
                nota.getItens().stream().map(ItemNotaSaidaResponse::from).toList(),
                nota.getCreatedAt(),
                nota.getUpdatedAt());
    }
}
