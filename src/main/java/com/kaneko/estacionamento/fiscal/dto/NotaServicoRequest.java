package com.kaneko.estacionamento.fiscal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de entrada para a nota de servico. O valor do ISS e o total sao
 * calculados pelo servico a partir do valor do servico, da aliquota e do desconto.
 */
public record NotaServicoRequest(

        @NotBlank(message = "O número da nota é obrigatório.")
        @Size(max = 20) String numero,

        @Size(max = 10) String modelo,
        @Size(max = 10) String serie,

        @NotNull(message = "O cliente é obrigatório.")
        Long clienteId,

        Long servicoId,
        Long movimentacaoId,
        Long mensalistaId,
        Long ordemServicoId,
        Long condicaoPagamentoId,
        Long formaPagamentoId,

        LocalDate dataEmissao,

        @NotNull(message = "O valor do serviço é obrigatório.")
        @PositiveOrZero(message = "O valor do serviço não pode ser negativo.")
        BigDecimal valorServico,

        @PositiveOrZero(message = "A alíquota de ISS não pode ser negativa.")
        @DecimalMax(value = "100.00", message = "A alíquota de ISS não pode exceder 100.")
        BigDecimal aliquotaIss,

        @PositiveOrZero(message = "O desconto não pode ser negativo.")
        BigDecimal valorDesconto,

        @Size(max = 255) String observacao
) {
}
