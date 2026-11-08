package com.kaneko.estacionamento.financeiro.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de entrada para uma despesa avulsa a pagar. O valor total, quando
 * omitido, e calculado como {@code valorOriginal - desconto + juros + multa}; a
 * data de emissao, quando omitida, assume a data atual.
 */
public record ContaPagarAvulsaRequest(

        @NotBlank(message = "A descrição é obrigatória.")
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
        String descricao,

        Long fornecedorId,
        Long formaPagamentoId,

        @Size(max = 20) String numeroNota,
        @Size(max = 10) String modelo,
        @Size(max = 10) String serie,

        @Min(value = 1, message = "O número da parcela deve ser maior ou igual a 1.")
        Integer numeroParcela,

        @Min(value = 1, message = "O total de parcelas deve ser maior ou igual a 1.")
        Integer totalParcelas,

        @NotNull(message = "O valor original é obrigatório.")
        @PositiveOrZero(message = "O valor original não pode ser negativo.")
        @Digits(integer = 13, fraction = 6)
        BigDecimal valorOriginal,

        @PositiveOrZero(message = "O desconto não pode ser negativo.")
        @Digits(integer = 13, fraction = 6)
        BigDecimal valorDesconto,

        @PositiveOrZero(message = "Os juros não podem ser negativos.")
        @Digits(integer = 13, fraction = 6)
        BigDecimal valorJuros,

        @PositiveOrZero(message = "A multa não pode ser negativa.")
        @Digits(integer = 13, fraction = 6)
        BigDecimal valorMulta,

        @PositiveOrZero(message = "O valor total não pode ser negativo.")
        @Digits(integer = 13, fraction = 6)
        BigDecimal valorTotal,

        LocalDate dataEmissao,

        @NotNull(message = "A data de vencimento é obrigatória.")
        LocalDate dataVencimento,

        @Size(max = 255) String observacao
) {
}
