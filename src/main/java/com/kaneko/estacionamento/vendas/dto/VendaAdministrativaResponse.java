package com.kaneko.estacionamento.vendas.dto;

import com.kaneko.estacionamento.vendas.StatusVendaAdministrativa;
import com.kaneko.estacionamento.vendas.VendaAdministrativa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record VendaAdministrativaResponse(
        Long id, Long organizacaoId, String numero, Long clienteId, String clienteNome,
        Long condicaoPagamentoId, String condicaoPagamentoNome,
        Long localEstoqueId, String localEstoqueNome, StatusVendaAdministrativa status,
        LocalDate dataEmissao, String moeda, BigDecimal subtotal,
        BigDecimal valorDesconto, BigDecimal valorTotal, String observacao,
        String motivoCancelamento, Long version, OffsetDateTime createdAt,
        OffsetDateTime updatedAt, List<ItemVendaAdministrativaResponse> itens) {
    public static VendaAdministrativaResponse from(VendaAdministrativa venda) {
        return new VendaAdministrativaResponse(venda.getId(), venda.getOrganizacaoId(),
                venda.getNumero(), venda.getCliente().getId(), venda.getCliente().getNome(),
                venda.getCondicaoPagamento() == null ? null : venda.getCondicaoPagamento().getId(),
                venda.getCondicaoPagamento() == null ? null : venda.getCondicaoPagamento().getNome(),
                venda.getLocalEstoque().getId(), venda.getLocalEstoque().getNome(),
                venda.getStatus(), venda.getDataEmissao(), venda.getMoeda(),
                venda.getSubtotal(), venda.getValorDesconto(), venda.getValorTotal(),
                venda.getObservacao(), venda.getMotivoCancelamento(), venda.getVersion(),
                venda.getCreatedAt(), venda.getUpdatedAt(),
                venda.getItens().stream().map(ItemVendaAdministrativaResponse::from).toList());
    }
}
