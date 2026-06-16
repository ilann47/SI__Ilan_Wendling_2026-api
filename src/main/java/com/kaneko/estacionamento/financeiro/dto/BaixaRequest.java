package com.kaneko.estacionamento.financeiro.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados para a baixa (pagamento/recebimento) de um titulo financeiro. O valor
 * informado e somado ao ja baixado; a data, quando omitida, assume a data atual.
 */
public record BaixaRequest(

        @NotNull(message = "O valor da baixa é obrigatório.")
        @Positive(message = "O valor da baixa deve ser maior que zero.")
        BigDecimal valor,

        LocalDate data
) {
}
