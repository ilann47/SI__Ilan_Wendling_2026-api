package com.kaneko.estacionamento.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OrdemCompraRequest(
        @NotBlank @Size(max = 40) String numero,
        @NotNull Long fornecedorId,
        LocalDate dataEmissao,
        LocalDate previsaoEntrega,
        @Pattern(regexp = "[A-Za-z]{3}") String moeda,
        @PositiveOrZero BigDecimal valorFrete,
        @PositiveOrZero BigDecimal valorDesconto,
        @Size(max = 500) String observacao,
        @NotEmpty @Valid List<ItemOrdemCompraRequest> itens) {
}
