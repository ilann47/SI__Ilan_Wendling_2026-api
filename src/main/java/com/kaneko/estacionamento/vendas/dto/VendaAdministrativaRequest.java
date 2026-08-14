package com.kaneko.estacionamento.vendas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VendaAdministrativaRequest(
        @NotBlank @Size(max = 40) String numero,
        @NotNull Long clienteId,
        Long condicaoPagamentoId,
        @NotNull Long localEstoqueId,
        LocalDate dataEmissao,
        @Size(max = 3) String moeda,
        @PositiveOrZero BigDecimal valorDesconto,
        @Size(max = 500) String observacao,
        @NotEmpty @Valid List<ItemVendaAdministrativaRequest> itens) {
}
