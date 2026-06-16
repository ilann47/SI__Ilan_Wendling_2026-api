package com.kaneko.estacionamento.financeiro.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados para o lancamento manual de um titulo a pagar (sem nota). O valor total,
 * quando omitido, e {@code valorOriginal - desconto + juros + multa}.
 */
public record ContaPagarRequest(

        @NotNull(message = "O fornecedor é obrigatório.")
        Long fornecedorId,

        Long condicaoPagamentoId,
        Long formaPagamentoId,

        @Min(value = 1, message = "O número da parcela deve ser maior ou igual a 1.")
        Integer numeroParcela,

        @Min(value = 1, message = "O total de parcelas deve ser maior ou igual a 1.")
        Integer totalParcelas,

        @NotNull(message = "O valor original é obrigatório.")
        @PositiveOrZero(message = "O valor original não pode ser negativo.")
        BigDecimal valorOriginal,

        @PositiveOrZero BigDecimal valorDesconto,
        @PositiveOrZero BigDecimal valorJuros,
        @PositiveOrZero BigDecimal valorMulta,
        @PositiveOrZero BigDecimal valorTotal,

        LocalDate dataEmissao,

        @NotNull(message = "A data de vencimento é obrigatória.")
        LocalDate dataVencimento,

        @Size(max = 255) String observacao
) {
}
