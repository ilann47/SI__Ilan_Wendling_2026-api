package com.kaneko.estacionamento.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record ReceberOrdemCompraRequest(
        @NotNull Long localEstoqueId,
        OffsetDateTime recebidoEm,
        @Size(max = 500) String observacao,
        @NotEmpty @Valid List<ItemRecebimentoCompraRequest> itens) {
}
