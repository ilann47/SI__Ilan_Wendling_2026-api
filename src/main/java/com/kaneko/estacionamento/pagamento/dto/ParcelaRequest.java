package com.kaneko.estacionamento.pagamento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Dados de entrada de uma parcela da condicao de pagamento.
 *
 * <p>As restricoes refletem o schema de {@code parcela_condicao_pagamento}:
 * {@code numero} sequencial (>= 1), {@code dias} nao negativo e {@code percentual}
 * nao negativo (limite superior de 100 validado pela regra de negocio/CHECK).
 * A forma de pagamento e opcional.
 */
public record ParcelaRequest(

        @NotNull(message = "O número da parcela é obrigatório.")
        @Positive(message = "O número da parcela deve ser maior ou igual a 1.")
        Integer numero,

        @NotNull(message = "Os dias da parcela são obrigatórios.")
        @PositiveOrZero(message = "Os dias da parcela não podem ser negativos.")
        Integer dias,

        @NotNull(message = "O percentual da parcela é obrigatório.")
        @PositiveOrZero(message = "O percentual da parcela não pode ser negativo.")
        BigDecimal percentual,

        Long formaPagamentoId
) {
}
