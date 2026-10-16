package com.kaneko.estacionamento.fiscal.importacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ConciliarImportacaoRequest(@NotNull Long fornecedorId, @NotNull Long localEstoqueId,
        Long condicaoPagamentoId, Long ordemCompraId, Long recebimentoCompraId, Boolean registrarNovoRecebimento,
        @NotEmpty @Size(max=990) @Valid List<Item> itens) {
    public record Item(@Min(1) int sequencia, @NotNull Long produtoId,
            @NotNull @Positive @Digits(integer=12,fraction=6) BigDecimal fatorConversao,
            @NotNull @PositiveOrZero @Digits(integer=13,fraction=2) BigDecimal valorUnitario,
            Long itemOrdemCompraId, @Size(max=300) String motivoDivergencia) { }
}
